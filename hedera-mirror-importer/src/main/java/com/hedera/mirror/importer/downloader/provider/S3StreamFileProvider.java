/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.importer.downloader.provider;

import static com.hedera.mirror.importer.domain.StreamFilename.EPOCH;
import static com.hedera.mirror.importer.domain.StreamFilename.FileType.SIGNATURE;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import lombok.CustomLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.FileTransformerConfiguration;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.RequestPayer;
import software.amazon.awssdk.services.s3.model.S3Object;

@CustomLog
@RequiredArgsConstructor
public final class S3StreamFileProvider implements StreamFileProvider {

    public static final String SEPARATOR = "/";
    private static final String TEMPLATE_ACCOUNT_ID_PREFIX = "%s/%s%s/";
    private static final String TEMPLATE_NODE_ID_PREFIX = "%s/%d/%d/%s/";
    private final CommonDownloaderProperties commonDownloaderProperties;
    private final Map<PathKey, PathResult> paths = new ConcurrentHashMap<>();
    private final S3AsyncClient s3Client;

    public Mono<StreamFileData> get(ConsensusNode node, StreamFilename streamFilename) {

        var s3Key = streamFilename.getFilePath();
        var request = GetObjectRequest.builder()
                .bucket(commonDownloaderProperties.getBucketName())
                .key(s3Key)
                .requestPayer(RequestPayer.REQUESTER)
                .build();

        var responseFuture = s3Client.getObject(request, AsyncResponseTransformer.toBytes());
        return Mono.fromFuture(responseFuture)
                .map(r -> new StreamFileData(
                        streamFilename, r.asByteArrayUnsafe(), r.response().lastModified()))
                .timeout(commonDownloaderProperties.getTimeout())
                .onErrorMap(NoSuchKeyException.class, TransientProviderException::new)
                .doOnSuccess(s -> log.debug("Finished downloading {}", s3Key));
    }

    @Override
    public Mono<GetObjectResponseWithKey> get(S3Object s3Object, Path downloadBase) {
        var s3Key = s3Object.key();
        log.debug("Starting download of {} to {}", s3Key, downloadBase);

        var request = GetObjectRequest.builder()
                .bucket(commonDownloaderProperties.getBucketName())
                .key(s3Key)
                .requestPayer(RequestPayer.REQUESTER)
                .build();

        var downloadPath = downloadBase.resolve(s3Key);
        var responseFuture = s3Client.getObject(
                        request,
                        AsyncResponseTransformer.toFile(
                                downloadPath, FileTransformerConfiguration.defaultCreateOrReplaceExisting()))
                .thenApply(response -> new GetObjectResponseWithKey(response, s3Key));

        return Mono.fromFuture(responseFuture)
                .timeout(commonDownloaderProperties.getTimeout())
                .onErrorMap(NoSuchKeyException.class, TransientProviderException::new)
                .doOnSuccess(s -> log.debug("Finished downloading {} to {}", s3Key, downloadBase));
    }

    @Override
    public Flux<StreamFileData> list(ConsensusNode node, StreamFilename lastFilename) {
        // Number of items we plan do download in a single batch times 2 for file + sig.
        int batchSize = commonDownloaderProperties.getBatchSize() * 2;

        var key = new PathKey(node, lastFilename.getStreamType());
        var pathResult = paths.computeIfAbsent(key, k -> new PathResult());
        var prefix = getPrefix(key, pathResult.getPathType());
        var startAfter = prefix + lastFilename.getFilenameAfter();

        var listRequest = ListObjectsV2Request.builder()
                .bucket(commonDownloaderProperties.getBucketName())
                .prefix(prefix)
                .delimiter(SEPARATOR)
                .startAfter(startAfter)
                .maxKeys(batchSize)
                .requestPayer(RequestPayer.REQUESTER)
                .build();

        return Mono.fromFuture(s3Client.listObjectsV2(listRequest))
                .timeout(commonDownloaderProperties.getTimeout())
                .doOnNext(l -> {
                    pathResult.update(!l.contents().isEmpty());
                    log.debug("Returned {} s3 objects", l.contents().size());
                })
                .flatMapIterable(ListObjectsV2Response::contents)
                .map(this::toStreamFilename)
                .filter(s -> s != EPOCH && s.getFileType() == SIGNATURE)
                .flatMapSequential(streamFilename -> get(node, streamFilename))
                .doOnSubscribe(s -> log.debug(
                        "Searching for the next {} files after {}/{}",
                        batchSize,
                        commonDownloaderProperties.getBucketName(),
                        startAfter))
                .switchIfEmpty(Flux.defer(() -> pathResult.fallback() ? list(node, lastFilename) : Flux.empty()));
    }

    @Override
    public Flux<S3Object> listAllPaginated(ConsensusNode node, StreamFilename lastFilename) {
        var prefix = getAccountIdPrefix(node, lastFilename.getStreamType());
        var startAfter = prefix + lastFilename.getFilenameAfter();
        var bucketName = commonDownloaderProperties.getBucketName();

        var listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .startAfter(startAfter)
                .delimiter(SEPARATOR)
                .requestPayer(RequestPayer.REQUESTER)
                .build();

        return Flux.from(s3Client.listObjectsV2Paginator(listRequest))
                .timeout(commonDownloaderProperties.getTimeout())
                .doOnNext(r -> {
                    if (log.isDebugEnabled()) {
                        var contents = r.contents();
                        if (contents.isEmpty()) {
                            log.debug("Next batch of s3 objects is empty");
                        } else {
                            log.debug(
                                    "Next batch of {} s3 objects, starting with: {}",
                                    contents.size(),
                                    contents.get(0).key());
                        }
                    }
                })
                .flatMapIterable(ListObjectsV2Response::contents)
                .doOnSubscribe(s -> log.debug("Listing files from bucket {} after {}", bucketName, startAfter));
    }

    private String getAccountIdPrefix(PathKey key) {
        return getAccountIdPrefix(key.node(), key.type());
    }

    private String getAccountIdPrefix(ConsensusNode node, StreamType streamType) {
        var nodeAccount = node.getNodeAccountId().toString();
        return TEMPLATE_ACCOUNT_ID_PREFIX.formatted(streamType.getPath(), streamType.getNodePrefix(), nodeAccount);
    }

    private String getNodeIdPrefix(PathKey key) {
        var network = commonDownloaderProperties.getMirrorProperties().getNetwork();
        var shard = commonDownloaderProperties.getMirrorProperties().getShard();
        var streamFolder = key.type().getNodeIdBasedSuffix();
        return TEMPLATE_NODE_ID_PREFIX.formatted(network, shard, key.node().getNodeId(), streamFolder);
    }

    private String getPrefix(PathKey key, PathType pathType) {
        return switch (pathType) {
            case ACCOUNT_ID, AUTO -> getAccountIdPrefix(key);
            case NODE_ID -> getNodeIdPrefix(key);
        };
    }

    private StreamFilename toStreamFilename(S3Object s3Object) {
        var key = s3Object.key();

        try {
            return StreamFilename.from(key, SEPARATOR);
        } catch (Exception e) {
            log.warn("Unable to parse stream filename for {}", key, e);
            return EPOCH; // Reactor doesn't allow null return values for map(), so use a sentinel that we filter later
        }
    }

    record PathKey(ConsensusNode node, StreamType type) {}

    @Data
    private class PathResult {

        @Nullable
        private volatile Instant expiration;

        private volatile PathType pathType = commonDownloaderProperties.getPathType();

        private PathResult() {
            if (commonDownloaderProperties.getPathType() == PathType.AUTO) {
                this.expiration = Instant.now().plus(commonDownloaderProperties.getPathRefreshInterval());
                this.pathType = PathType.ACCOUNT_ID;
            }
        }

        void update(boolean found) {
            // Path is statically configured or has permanently transitioned from ACCOUNT_ID to NODE_ID
            if (expiration == null) {
                return;
            }

            // Permanently switch to NODE_ID
            if (found && pathType == PathType.NODE_ID) {
                expiration = null;
                return;
            }

            // NODE_ID attempt failed so revert back to ACCOUNT_ID for now
            if (!found && pathType == PathType.NODE_ID) {
                pathType = PathType.ACCOUNT_ID;
                return;
            }

            // If ACCOUNT_ID auto mode interval has expired, try NODE_ID if no files were found
            var now = Instant.now();
            if (now.isAfter(expiration)) {
                expiration = now.plus(commonDownloaderProperties.getPathRefreshInterval());
                if (!found) {
                    pathType = PathType.NODE_ID;
                }
            }
        }

        boolean fallback() {
            return expiration != null && pathType == PathType.NODE_ID;
        }
    }
}
