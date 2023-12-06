/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.mirror.web3.exception.BlockNumberOutOfRangeException;
import com.hedera.mirror.web3.viewmodel.BlockType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ContractCallServiceERCTokenTest extends ContractCallTestSetup {

    private static Stream<Arguments> ercContractFunctionArgumentsProvider() {
        return Arrays.stream(ErcContractReadOnlyFunctions.values())
                .flatMap(ercFunction -> Stream.of(Arguments.of(ercFunction, true), Arguments.of(ercFunction, false)));
    }

    private static Stream<Arguments> ercContractFunctionArgumentsProviderHistorical() {
        List<String> blockNumbers = List.of(
                //                String.valueOf(BlockType.EARLIEST.number()), TODO: this fails now but Kris will push a
                // fix - uncomment after that
                //                String.valueOf(htsAndErcBlock - 1), TODO: same as above
                String.valueOf(htsAndErcBlock), String.valueOf(BlockType.LATEST.number()));

        return Arrays.stream(ErcContractReadOnlyFunctions.values())
                .flatMap(ercFunction -> Stream.concat(
                        blockNumbers.stream().map(blockNumber -> Arguments.of(ercFunction, true, blockNumber)),
                        blockNumbers.stream().map(blockNumber -> Arguments.of(ercFunction, false, blockNumber))));
    }

    public static final String REDIRECT_SUFFIX = "Redirect";
    public static final String NON_STATIC_SUFFIX = "NonStatic";

    @ParameterizedTest
    @MethodSource("ercContractFunctionArgumentsProviderHistorical")
    void ercReadOnlyPrecompileOperationsTest(
            final ErcContractReadOnlyFunctions ercFunction, final boolean isStatic, final String blockNumber) {
        final var functionName = ercFunction.getName(isStatic);
        final var functionHash =
                functionEncodeDecoder.functionHashFor(functionName, ERC_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, ERC_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.of(blockNumber));

        final var successfulResponse = functionEncodeDecoder.encodedResultFor(
                ercFunction.name, ERC_ABI_PATH, ercFunction.expectedResultFields);
        final var errorResponse = Bytes.EMPTY.toHexString();
        final var latestBlockNumber = recordFileRepository.findLatestIndex().orElse(Long.MAX_VALUE);

        if (Long.parseLong(blockNumber) > latestBlockNumber) {
            assertThatThrownBy(() -> contractCallService.processCall(serviceParameters))
                    .isInstanceOf(BlockNumberOutOfRangeException.class);
        } else if (Long.parseLong(blockNumber) < htsAndErcBlock) {
            assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(errorResponse);
        } else {
            assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulResponse);
        }
    }

    @ParameterizedTest
    @MethodSource("ercContractFunctionArgumentsProvider")
    void supportedErcReadOnlyPrecompileOperationsTest(
            final ErcContractReadOnlyFunctions ercFunction, final boolean isStatic) {
        final var functionName = ercFunction.getName(isStatic);
        final var functionHash =
                functionEncodeDecoder.functionHashFor(functionName, ERC_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, ERC_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(ErcContractModificationFunctions.class)
    void supportedErcModificationPrecompileOperationsTest(final ErcContractModificationFunctions ercFunction) {
        final var functionHash =
                functionEncodeDecoder.functionHashFor(ercFunction.name, ERC_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, ERC_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(ErcContractReadOnlyFunctions.class)
    void supportedErcReadOnlyRedirectPrecompileOperationsTest(final ErcContractReadOnlyFunctions ercFunction) {
        final var functionName = ercFunction.name + REDIRECT_SUFFIX;
        final var functionHash = functionEncodeDecoder.functionHashFor(
                functionName, REDIRECT_CONTRACT_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, REDIRECT_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(ErcContractModificationFunctions.class)
    void supportedErcModificationsRedirectPrecompileOperationsTest(final ErcContractModificationFunctions ercFunction) {
        final var functionName = ercFunction.name + "Redirect";
        final var functionHash = functionEncodeDecoder.functionHashFor(
                functionName, REDIRECT_CONTRACT_ABI_PATH, ercFunction.functionParameters);
        final var serviceParameters = serviceParametersForExecution(
                functionHash, REDIRECT_CONTRACT_ADDRESS, ETH_ESTIMATE_GAS, 0L, BlockType.LATEST);

        final var expectedGasUsed = gasUsedAfterExecution(serviceParameters);

        assertThat(isWithinExpectedGasRange(
                        longValueOf.applyAsLong(contractCallService.processCall(serviceParameters)), expectedGasUsed))
                .isTrue();
    }

    @Test
    void delegateTransferDoesNotExecuteAndReturnEmpty() {
        final var functionHash = functionEncodeDecoder.functionHashFor(
                "delegateTransfer", ERC_ABI_PATH, FUNGIBLE_TOKEN_ADDRESS, SPENDER_ADDRESS, 2L);
        final var serviceParameters =
                serviceParametersForExecution(functionHash, ERC_CONTRACT_ADDRESS, ETH_CALL, 0L, BlockType.LATEST);
        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x");
    }

    @RequiredArgsConstructor
    public enum ErcContractReadOnlyFunctions {
        GET_APPROVED_EMPTY_SPENDER("getApproved", new Object[] {NFT_ADDRESS, 2L}, new Address[] {Address.ZERO}),
        IS_APPROVE_FOR_ALL(
                "isApprovedForAll", new Address[] {NFT_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS}, new Boolean[] {true}),
        IS_APPROVE_FOR_ALL_WITH_ALIAS(
                "isApprovedForAll", new Address[] {NFT_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS}, new Boolean[] {true}),
        ALLOWANCE_OF(
                "allowance", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS, SPENDER_ADDRESS}, new Long[] {13L}),
        ALLOWANCE_OF_WITH_ALIAS(
                "allowance", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS}, new Long[] {13L}),
        GET_APPROVED("getApproved", new Object[] {NFT_ADDRESS, 1L}, new Address[] {SPENDER_ALIAS}),
        ERC_DECIMALS("decimals", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Integer[] {12}),
        TOTAL_SUPPLY("totalSupply", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new Long[] {12345L}),
        ERC_SYMBOL("symbol", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new String[] {"HBAR"}),
        BALANCE_OF("balanceOf", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ADDRESS}, new Long[] {12L}),
        BALANCE_OF_WITH_ALIAS("balanceOf", new Address[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS}, new Long[] {12L}),
        ERC_NAME("name", new Address[] {FUNGIBLE_TOKEN_ADDRESS}, new String[] {"Hbars"}),
        OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS, 1L}, new Address[] {OWNER_ADDRESS}),
        EMPTY_OWNER_OF("getOwnerOf", new Object[] {NFT_ADDRESS, 2L}, new Address[] {Address.ZERO}),
        TOKEN_URI("tokenURI", new Object[] {NFT_ADDRESS, 1L}, new String[] {"NFT_METADATA_URI"});

        private final String name;
        private final Object[] functionParameters;
        private final Object[] expectedResultFields;

        public String getName(final boolean isStatic) {
            return isStatic ? name : name + NON_STATIC_SUFFIX;
        }
    }

    @RequiredArgsConstructor
    public enum ErcContractModificationFunctions {
        APPROVE("approve", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SPENDER_ALIAS, 2L}),
        DELETE_ALLOWANCE_NFT("approve", new Object[] {NFT_ADDRESS, Address.ZERO, 1L}),
        APPROVE_NFT("approve", new Object[] {NFT_ADDRESS, SPENDER_ADDRESS, 1L}),
        APPROVE_WITH_ALIAS("approve", new Object[] {FUNGIBLE_TOKEN_ADDRESS, SENDER_ALIAS, 2L}),
        TRANSFER("transfer", new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, 2L}),
        TRANSFER_FROM("transferFrom", new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS, 2L}),
        TRANSFER_FROM_NFT("transferFromNFT", new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ALIAS, 1L}),
        TRANSFER_WITH_ALIAS("transfer", new Object[] {TREASURY_TOKEN_ADDRESS, SPENDER_ALIAS, 2L}),
        TRANSFER_FROM_WITH_ALIAS(
                "transferFrom", new Object[] {TREASURY_TOKEN_ADDRESS, SENDER_ALIAS, SPENDER_ALIAS, 2L}),
        TRANSFER_FROM_NFT_WITH_ALIAS(
                "transferFromNFT", new Object[] {NFT_TRANSFER_ADDRESS, OWNER_ADDRESS, SPENDER_ALIAS, 1L});
        private final String name;
        private final Object[] functionParameters;
    }
}
