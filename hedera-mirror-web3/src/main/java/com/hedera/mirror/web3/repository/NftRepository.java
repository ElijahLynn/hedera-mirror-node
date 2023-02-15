package com.hedera.mirror.web3.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftId;

public interface NftRepository extends CrudRepository<Nft, NftId> {

    @Query(value = "select spender from nft where token_id = ?1 and serial_number = ?2",
            nativeQuery = true)
    long findSpender(final Long tokenId, final Long serialNo);

    @Query(value = "select account_id from nft where token_id = ?1 and serial_number = ?2",
            nativeQuery = true)
    long findOwner(final Long tokenId, final Long serialNo);

    @Query(value = "select metadata from nft where token_id = ?1 and serial_number = ?2",
            nativeQuery = true)
    Optional<byte[]> findMetadata(final Long tokenId, final Long serialNo);
}
