/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.evm.contracts.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaSelfDestructOperationV038Test {

    static final Address BENEFICIARY = Address.fromHexString("0x0000000000000000000000000000000000000929");
    static final Address BENEFICIARY_SYSTEM_ACCOUNT =
            Address.fromHexString("0x00000000000000000000000000000000000002ED");
    static final String ETH_ADDRESS = "0xc257274276a4e539741ca11b590b9447b26a8051";
    static final Address EIP_1014_ETH_ADDRESS = Address.fromHexString(ETH_ADDRESS);

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame frame;

    @Mock
    private EVM evm;

    @Mock
    private MutableAccount account;

    @Mock
    private BiPredicate<Address, MessageFrame> addressValidator;

    @Mock
    private Predicate<Address> systemAccountDetector;

    private HederaSelfDestructOperationV038 subject;

    @BeforeEach
    void setUp() {
        subject = new HederaSelfDestructOperationV038(gasCalculator, addressValidator, systemAccountDetector);

        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(gasCalculator.selfDestructOperationGasCost(any(), eq(Wei.ONE))).willReturn(2L);
    }

    @Test
    void delegatesToSuperWhenValid() {
        givenRubberstampValidator();

        given(frame.getStackItem(0)).willReturn(BENEFICIARY);
        given(frame.popStackItem()).willReturn(BENEFICIARY);
        given(frame.getRecipientAddress()).willReturn(EIP_1014_ETH_ADDRESS);

        given(worldUpdater.get(any())).willReturn(account);
        given(worldUpdater.getAccount(any())).willReturn(account);
        given(account.getBalance()).willReturn(Wei.ONE);
        given(frame.isStatic()).willReturn(true);
        given(gasCalculator.getColdAccountAccessCost()).willReturn(1L);

        final var opResult = subject.execute(frame, evm);

        assertEquals(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE, opResult.getHaltReason());
        assertEquals(3L, opResult.getGasCost());
    }

    @Test
    void rejectsSelfDestructToSelf() {
        givenRubberstampValidator();

        given(frame.getStackItem(0)).willReturn(EIP_1014_ETH_ADDRESS);
        given(frame.getRecipientAddress()).willReturn(EIP_1014_ETH_ADDRESS);

        final var opResult = subject.execute(frame, evm);

        assertEquals(HederaExceptionalHaltReason.SELF_DESTRUCT_TO_SELF, opResult.getHaltReason());
        assertEquals(2L, opResult.getGasCost());
    }

    @Test
    void executeInvalidSolidityAddress() {
        givenRejectingValidator();

        given(frame.getStackItem(0)).willReturn(BENEFICIARY_SYSTEM_ACCOUNT);

        final var opResult = subject.execute(frame, evm);

        assertEquals(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, opResult.getHaltReason());
        assertEquals(2L, opResult.getGasCost());
    }

    @Test
    void haltsWhenBeneficiaryIsSystemAccount() {
        given(frame.getStackItem(0)).willReturn(BENEFICIARY);
        given(frame.getRecipientAddress()).willReturn(EIP_1014_ETH_ADDRESS);
        given(systemAccountDetector.test(any())).willReturn(true);
        // when
        final var result = subject.execute(frame, evm);
        // then
        assertEquals(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, result.getHaltReason());
        assertEquals(2L, result.getGasCost());
    }

    private void givenRubberstampValidator() {
        given(addressValidator.test(any(), any())).willReturn(true);
    }

    private void givenRejectingValidator() {
        given(addressValidator.test(any(), any())).willReturn(false);
    }
}
