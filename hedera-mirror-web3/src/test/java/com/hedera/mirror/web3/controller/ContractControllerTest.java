/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.controller;

import static com.hedera.mirror.web3.validation.HexValidator.MESSAGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;

import com.hedera.mirror.web3.exception.BlockNumberNotFoundException;
import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.exception.InvalidParametersException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.viewmodel.ContractCallRequest;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse;
import io.github.bucket4j.Bucket;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = ContractController.class)
class ContractControllerTest {

    private static final String CALL_URI = "/api/v1/contracts/call";
    private static final String BYTES = "6080";

    @Resource
    private WebTestClient webClient;

    @MockBean
    private ContractCallService service;

    @MockBean
    private Bucket bucket;

    @BeforeEach
    void setUp() {
        given(bucket.tryConsume(1)).willReturn(true);
    }

    @NullAndEmptySource
    @ValueSource(strings = {"0x00000000000000000000000000000000000007e7"})
    @ParameterizedTest
    void estimateGas(String to) {
        final var request = request();
        request.setEstimate(true);
        request.setTo(to);
        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(OK);
    }

    @ValueSource(longs = {2000, -2000, Long.MAX_VALUE, 0})
    @ParameterizedTest
    void estimateGasWithInvalidGasParameter(long gas) {
        final var errorString = gas < 21000L
                ? numberErrorString("gas", "greater", 21000L)
                : numberErrorString("gas", "less", 15_000_000L);
        final var request = request();
        request.setEstimate(true);
        request.setGas(gas);
        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(errorString));
    }

    @Test
    void exceedingRateLimit() {
        for (var i = 0; i < 3; i++) {
            webClient
                    .post()
                    .uri(CALL_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(request()))
                    .exchange()
                    .expectStatus()
                    .isEqualTo(OK);
        }
        given(bucket.tryConsume(1)).willReturn(false);

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request()))
                .exchange()
                .expectStatus()
                .isEqualTo(TOO_MANY_REQUESTS);
    }

    @NullAndEmptySource
    @ValueSource(
            strings = {
                " ",
                "0x",
                "0xghijklmno",
                "0x00000000000000000000000000000000000004e",
                "0x00000000000000000000000000000000000004e2a",
                "0x000000000000000000000000000000Z0000007e7",
                "00000000001239847e"
            })
    @ParameterizedTest
    void callInvalidTo(String to) {
        final var request = request();
        request.setTo(to);

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class);
    }

    @Test
    void callMissingTo() {
        final var exceptionMessage = "No such contract or token";
        final var request = request();

        given(service.processCall(any())).willThrow(new EntityNotFoundException(exceptionMessage));

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(NOT_FOUND)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(exceptionMessage));
    }

    @EmptySource
    @ValueSource(
            strings = {
                " ",
                "0x",
                "0xghijklmno",
                "0x00000000000000000000000000000000000004e",
                "0x00000000000000000000000000000000000004e2a",
                "0x000000000000000000000000000000Z0000007e7",
                "00000000001239847e"
            })
    @ParameterizedTest
    void callInvalidFrom(String from) {
        final var errorString = "from field ".concat(MESSAGE);
        final var request = request();
        request.setFrom(from);

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(errorString));
    }

    @Test
    void callInvalidValue() {
        final var request = request();
        request.setValue(-1L);

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse("value field must be greater than or equal to 0"));
    }

    @Test
    void exceedingDataCallSizeOnEstimate() {
        final var request = request();
        request.setData("0x" + BYTES.repeat(4000));
        request.setEstimate(true);

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse("data field must not exceed call size limit"));
    }

    @Test
    void exceedingDataCreateSizeOnEstimate() {
        final var request = request();

        request.setTo(null);
        request.setData("0x" + BYTES.repeat(20000));
        request.setEstimate(true);

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse("data field invalid hexadecimal string"));
    }

    @Test
    void callWithMalformedJsonBody() {
        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue("{from: 0x00000000000000000000000000000000000004e2"))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(
                        "Failed to read HTTP message", "Unable to parse JSON", StringUtils.EMPTY));
    }

    @Test
    void callWithUnsupportedMediaTypeBody() {
        final var request = request();

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.TEXT_PLAIN)
                .body(BodyInserters.fromValue(request.toString()))
                .exchange()
                .expectStatus()
                .isEqualTo(UNSUPPORTED_MEDIA_TYPE)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(
                        "Unsupported Media Type",
                        "Content type 'text/plain' not supported for bodyType=com.hedera.mirror.web3.viewmodel"
                                + ".ContractCallRequest",
                        StringUtils.EMPTY));
    }

    @Test
    void callRevertMethodAndExpectDetailMessage() {
        final var detailedErrorMessage = "Custom revert message";
        final var hexDataErrorMessage =
                "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000015437573746f6d20726576657274206d6573736167650000000000000000000000";
        final var request = request();
        request.setData("0xa26388bb");

        given(service.processCall(any()))
                .willThrow(new MirrorEvmTransactionException(
                        CONTRACT_REVERT_EXECUTED, detailedErrorMessage, hexDataErrorMessage));

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(
                        CONTRACT_REVERT_EXECUTED.name(), detailedErrorMessage, hexDataErrorMessage));
    }

    @Test
    void callWithInvalidParameter() {
        final var ERROR_MESSAGE = "No such contract or token";
        final var request = request();

        given(service.processCall(any())).willThrow(new InvalidParametersException(ERROR_MESSAGE));

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(ERROR_MESSAGE));
    }

    @Test
    void callInvalidGasPrice() {
        final var errorString = numberErrorString("gasPrice", "greater", 0);
        final var request = request();
        request.setGasPrice(-1L);

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(errorString));
    }

    @Test
    void transferWithoutSender() {
        final var errorString = "from field must not be empty";
        final var request = request();
        request.setFrom(null);

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(BAD_REQUEST)
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse(errorString));
    }

    @NullAndEmptySource
    @ParameterizedTest
    @ValueSource(strings = {"earliest", "latest", "0", "0x1a", "pending", "safe", "finalized"})
    void callValidBlockType(String value) {
        final var request = request();
        request.setBlock(BlockType.of(value));

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(OK);
    }

    @Test
    void callWithBlockNumberOutOfRangeExceptionTest() {
        final var request = request();
        given(service.processCall(any())).willThrow(new BlockNumberOutOfRangeException("Unknown block number"));

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse("Unknown block number"));
    }

    @Test
    void callWithBlockNumberNotFoundExceptionTest() {
        final var request = request();
        given(service.processCall(any())).willThrow(new BlockNumberNotFoundException());

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(GenericErrorResponse.class)
                .isEqualTo(new GenericErrorResponse("Unknown block number"));
    }

    @Test
    void callSuccess() {
        final var request = request();
        request.setData("0x1079023a0000000000000000000000000000000000000000000000000000000000000156");
        request.setValue(0);

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus()
                .isEqualTo(OK);
    }

    @Test
    void transferSuccess() {
        final var request = request();
        request.setData(null);

        webClient
                .post()
                .uri(CALL_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request()))
                .exchange()
                .expectStatus()
                .isEqualTo(OK);
    }

    @Test
    void callSuccessCors() {
        webClient
                .options()
                /**
                 * https://stackoverflow.com/questions/62723224/webtestclient-cors-with-spring-boot-and-webflux
                 * The Spring WebTestClient CORS testing requires that the URI contain any hostname and port.
                 */
                .uri("http://localhost" + CALL_URI)
                .header("Origin", "http://example.com")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectHeader()
                .valueEquals("Access-Control-Allow-Origin", "*")
                .expectHeader()
                .valueEquals("Access-Control-Allow-Methods", "POST");
    }

    private ContractCallRequest request() {
        final var request = new ContractCallRequest();
        request.setFrom("0x00000000000000000000000000000000000004e2");
        request.setGas(200000L);
        request.setGasPrice(78282329L);
        request.setTo("0x00000000000000000000000000000000000004e4");
        request.setValue(23);
        request.setData("0x1079023a");
        request.setBlock(BlockType.LATEST);
        return request;
    }

    private String numberErrorString(String field, String direction, long num) {
        return String.format("%s field must be %s than or equal to %d", field, direction, num);
    }
}
