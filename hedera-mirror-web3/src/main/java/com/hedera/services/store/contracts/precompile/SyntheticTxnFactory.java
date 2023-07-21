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

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.EMPTY_KEY;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenFreezeUnfreezeWrapper;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.DeleteWrapper;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.contracts.precompile.codec.PauseWrapper;
import com.hedera.services.store.contracts.precompile.codec.SetApprovalForAllWrapper;
import com.hedera.services.store.contracts.precompile.codec.UnpauseWrapper;
import com.hedera.services.store.contracts.precompile.codec.WipeWrapper;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import lombok.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import lombok.NonNull;

public class SyntheticTxnFactory {
    public static final String HTS_PRECOMPILED_CONTRACT_ADDRESS = "0x167";
    public static final ContractID HTS_PRECOMPILE_MIRROR_ID = EntityIdUtils.contractIdFromEvmAddress(
            Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArrayUnsafe());

    public static final String AUTO_MEMO = "auto-created account";
    private static final String LAZY_MEMO = "lazy-created account";
    private static final long THREE_MONTHS_IN_SECONDS = 7776000L;

    public TransactionBody.Builder createMint(final MintWrapper mintWrapper) {
        final var builder = TokenMintTransactionBody.newBuilder();

        builder.setToken(mintWrapper.tokenType());
        if (mintWrapper.type() == NON_FUNGIBLE_UNIQUE) {
            builder.addAllMetadata(mintWrapper.metadata());
        } else {
            builder.setAmount(mintWrapper.amount());
        }

        return TransactionBody.newBuilder().setTokenMint(builder);
    }

    public TransactionBody.Builder createHollowAccount(final ByteString alias, final long balance) {
        final var baseBuilder = createAccountBase(balance);
        baseBuilder.setKey(asKeyUnchecked(EMPTY_KEY)).setAlias(alias).setMemo(LAZY_MEMO);
        return TransactionBody.newBuilder().setCryptoCreateAccount(baseBuilder.build());
    }

    public TransactionBody.Builder createAccount(
            final ByteString alias, final Key key, final long balance, final int maxAutoAssociations) {
        final var baseBuilder = createAccountBase(balance);
        baseBuilder.setKey(key).setAlias(alias).setMemo(AUTO_MEMO);

        if (maxAutoAssociations > 0) {
            baseBuilder.setMaxAutomaticTokenAssociations(maxAutoAssociations);
        }
        return TransactionBody.newBuilder().setCryptoCreateAccount(baseBuilder.build());
    }

    private CryptoCreateTransactionBody.Builder createAccountBase(final long balance) {
        return CryptoCreateTransactionBody.newBuilder()
                .setInitialBalance(balance)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(THREE_MONTHS_IN_SECONDS));
    }

    public TransactionBody.Builder createAssociate(final Association association) {
        final var builder = TokenAssociateTransactionBody.newBuilder();

        builder.setAccount(association.accountId());
        builder.addAllTokens(association.tokenIds());

        return TransactionBody.newBuilder().setTokenAssociate(builder);
    }

    public TransactionBody.Builder createDissociate(final Dissociation dissociation) {
        final var builder = TokenDissociateTransactionBody.newBuilder();

        builder.setAccount(dissociation.accountId());
        builder.addAllTokens(dissociation.tokenIds());

        return TransactionBody.newBuilder().setTokenDissociate(builder);
    }

    /**
     * Copied Logic type from hedera-services.
     *
     * Differences with the original:
     *  1. Using {@link Id} instead of EntityId as types for the owner and operator
     * */
    public TransactionBody.Builder createFungibleApproval(
            @NonNull final ApproveWrapper approveWrapper, @NonNull Id ownerId) {
        return createNonfungibleApproval(approveWrapper, ownerId, null);
    }

    /**
     * Copied Logic type from hedera-services.
     *
     * Differences with the original:
     *  1. Using {@link Id} instead of EntityId as types for the owner and operator
     * */
    public TransactionBody.Builder createNonfungibleApproval(
            final ApproveWrapper approveWrapper, @Nullable final Id ownerId, @Nullable final Id operatorId) {
        final var builder = CryptoApproveAllowanceTransactionBody.newBuilder();
        if (approveWrapper.isFungible()) {
            var tokenAllowance = TokenAllowance.newBuilder()
                    .setTokenId(approveWrapper.tokenId())
                    .setOwner(Objects.requireNonNull(ownerId).asGrpcAccount())
                    .setSpender(approveWrapper.spender())
                    .setAmount(approveWrapper.amount().longValueExact());
            builder.addTokenAllowances(tokenAllowance.build());
        } else {
            final var op = NftAllowance.newBuilder()
                    .setTokenId(approveWrapper.tokenId())
                    .setSpender(approveWrapper.spender())
                    .addSerialNumbers(approveWrapper.serialNumber().longValueExact());
            if (ownerId != null) {
                op.setOwner(ownerId.asGrpcAccount());
                if (!ownerId.equals(operatorId)) {
                    op.setDelegatingSpender(Objects.requireNonNull(operatorId).asGrpcAccount());
                }
            }
            builder.addNftAllowances(op.build());
        }
        return TransactionBody.newBuilder().setCryptoApproveAllowance(builder);
    }

    /**
     * Copied Logic type from hedera-services.
     *
     * Differences with the original:
     *  1. Using {@link Id} instead of EntityId as types for the owner and operator
     * */
    public TransactionBody.Builder createDeleteAllowance(final ApproveWrapper approveWrapper, final Id owner) {
        final var builder = CryptoDeleteAllowanceTransactionBody.newBuilder();
        builder.addAllNftAllowances(List.of(NftRemoveAllowance.newBuilder()
                        .setOwner(owner.asGrpcAccount())
                        .setTokenId(approveWrapper.tokenId())
                        .addAllSerialNumbers(
                                List.of(approveWrapper.serialNumber().longValueExact()))
                        .build()))
                .build();
        return TransactionBody.newBuilder().setCryptoDeleteAllowance(builder);
    }

    public TransactionBody.Builder createBurn(final BurnWrapper burnWrapper) {
        final var builder = TokenBurnTransactionBody.newBuilder();

        builder.setToken(burnWrapper.tokenType());
        if (burnWrapper.type() == NON_FUNGIBLE_UNIQUE) {
            builder.addAllSerialNumbers(burnWrapper.serialNos());
        } else {
            builder.setAmount(burnWrapper.amount());
        }
        return TransactionBody.newBuilder().setTokenBurn(builder);
    }

    public TransactionBody.Builder createDelete(final DeleteWrapper deleteWrapper) {
        final var builder = TokenDeleteTransactionBody.newBuilder();
        builder.setToken(deleteWrapper.tokenID());
        return TransactionBody.newBuilder().setTokenDeletion(builder);
    }

    public TransactionBody.Builder createWipe(final WipeWrapper wipeWrapper) {
        final var builder = TokenWipeAccountTransactionBody.newBuilder();

        builder.setToken(wipeWrapper.token());
        builder.setAccount(wipeWrapper.account());
        if (wipeWrapper.type() == NON_FUNGIBLE_UNIQUE) {
            builder.addAllSerialNumbers(wipeWrapper.serialNumbers());
        } else {
            builder.setAmount(wipeWrapper.amount());
        }

        return TransactionBody.newBuilder().setTokenWipe(builder);
    }

    public TransactionBody.Builder createRevokeKyc(final GrantRevokeKycWrapper<TokenID, AccountID> wrapper) {
        final var builder = TokenRevokeKycTransactionBody.newBuilder();

        builder.setToken(wrapper.token());
        builder.setAccount(wrapper.account());

        return TransactionBody.newBuilder().setTokenRevokeKyc(builder);
    }

    public TransactionBody.Builder createGrantKyc(
            final GrantRevokeKycWrapper<TokenID, AccountID> grantRevokeKycWrapper) {
        final var builder = TokenGrantKycTransactionBody.newBuilder();

        builder.setToken(grantRevokeKycWrapper.token());
        builder.setAccount(grantRevokeKycWrapper.account());

        return TransactionBody.newBuilder().setTokenGrantKyc(builder);
    }

    public TransactionBody.Builder createApproveAllowanceForAllNFT(
            @NonNull final SetApprovalForAllWrapper setApprovalForAllWrapper, @NonNull Id ownerId) {

        final var builder = CryptoApproveAllowanceTransactionBody.newBuilder();

        builder.addNftAllowances(NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(setApprovalForAllWrapper.approved()))
                .setTokenId(setApprovalForAllWrapper.tokenId())
                .setOwner(Objects.requireNonNull(ownerId).asGrpcAccount())
                .setSpender(setApprovalForAllWrapper.to())
                .build());

        return TransactionBody.newBuilder().setCryptoApproveAllowance(builder);
    }

    public TransactionBody.Builder createUnpause(final UnpauseWrapper unpauseWrapper) {
        final var builder = TokenUnpauseTransactionBody.newBuilder();
        builder.setToken(unpauseWrapper.token());
        return TransactionBody.newBuilder().setTokenUnpause(builder);
    }

    public TransactionBody.Builder createFreeze(final TokenFreezeUnfreezeWrapper<TokenID, AccountID> freezeWrapper) {
        final var builder = TokenFreezeAccountTransactionBody.newBuilder();
        builder.setToken(freezeWrapper.token());
        builder.setAccount(freezeWrapper.account());
        return TransactionBody.newBuilder().setTokenFreeze(builder);
    }

    public TransactionBody.Builder createUnfreeze(
            final TokenFreezeUnfreezeWrapper<TokenID, AccountID> unFreezeWrapper) {
        final var builder = TokenUnfreezeAccountTransactionBody.newBuilder();
        builder.setToken(unFreezeWrapper.token());
        builder.setAccount(unFreezeWrapper.account());
        return TransactionBody.newBuilder().setTokenUnfreeze(builder);
    }

    public TransactionBody.Builder createPause(final PauseWrapper pauseWrapper) {
        final var builder = TokenPauseTransactionBody.newBuilder();
        builder.setToken(pauseWrapper.token());
        return TransactionBody.newBuilder().setTokenPause(builder);
    }

    public TransactionBody.Builder createTokenCreate(TokenCreateWrapper tokenCreateWrapper) {
        final var txnBodyBuilder = TokenCreateTransactionBody.newBuilder();
        txnBodyBuilder.setName(tokenCreateWrapper.getName());
        txnBodyBuilder.setSymbol(tokenCreateWrapper.getSymbol());
        txnBodyBuilder.setDecimals(tokenCreateWrapper.getDecimals().intValue());
        txnBodyBuilder.setTokenType(tokenCreateWrapper.isFungible() ? TokenType.FUNGIBLE_COMMON : NON_FUNGIBLE_UNIQUE);
        txnBodyBuilder.setSupplyType(
                tokenCreateWrapper.isSupplyTypeFinite() ? TokenSupplyType.FINITE : TokenSupplyType.INFINITE);
        txnBodyBuilder.setMaxSupply(tokenCreateWrapper.getMaxSupply());
        txnBodyBuilder.setInitialSupply(tokenCreateWrapper.getInitSupply().longValueExact());
        if (tokenCreateWrapper.getTreasury() != null) {
            txnBodyBuilder.setTreasury(tokenCreateWrapper.getTreasury());
        }
        txnBodyBuilder.setFreezeDefault(tokenCreateWrapper.isFreezeDefault());
        txnBodyBuilder.setMemo(tokenCreateWrapper.getMemo());
        if (tokenCreateWrapper.getExpiry().second() != 0) {
            txnBodyBuilder.setExpiry(Timestamp.newBuilder()
                    .setSeconds(tokenCreateWrapper.getExpiry().second())
                    .build());
        }
        if (tokenCreateWrapper.getExpiry().autoRenewAccount() != null) {
            txnBodyBuilder.setAutoRenewAccount(tokenCreateWrapper.getExpiry().autoRenewAccount());
        }
        if (tokenCreateWrapper.getExpiry().autoRenewPeriod() != 0) {
            txnBodyBuilder.setAutoRenewPeriod(Duration.newBuilder()
                    .setSeconds(tokenCreateWrapper.getExpiry().autoRenewPeriod()));
        }
        txnBodyBuilder.addAllCustomFees(tokenCreateWrapper.getFixedFees().stream()
                .map(TokenCreateWrapper.FixedFeeWrapper::asGrpc)
                .toList());
        txnBodyBuilder.addAllCustomFees(tokenCreateWrapper.getFractionalFees().stream()
                .map(TokenCreateWrapper.FractionalFeeWrapper::asGrpc)
                .toList());
        txnBodyBuilder.addAllCustomFees(tokenCreateWrapper.getRoyaltyFees().stream()
                .map(TokenCreateWrapper.RoyaltyFeeWrapper::asGrpc)
                .toList());
        return TransactionBody.newBuilder().setTokenCreation(txnBodyBuilder);
    }

    public TransactionBody.Builder createTransactionCall(final long gas, final Bytes functionParameters) {
        final var builder = ContractCallTransactionBody.newBuilder();

        builder.setContractID(HTS_PRECOMPILE_MIRROR_ID);
        builder.setGas(gas);
        builder.setFunctionParameters(ByteString.copyFrom(functionParameters.toArray()));

        return TransactionBody.newBuilder().setContractCall(builder);
    }
}
