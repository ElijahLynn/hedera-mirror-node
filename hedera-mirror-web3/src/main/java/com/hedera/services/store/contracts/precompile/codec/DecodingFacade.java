/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.codec;

import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.EXPIRY;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.EXPIRY_V2;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.TOKEN_KEY;
import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;

import com.hedera.services.store.contracts.precompile.FungibleTokenTransfer;
import com.hedera.services.store.contracts.precompile.NftExchange;
import com.hedera.services.utils.EntityIdUtils;

@Singleton
public class DecodingFacade {
    private static final int WORD_LENGTH = 32;
    private static final int ADDRESS_BYTES_LENGTH = 20;
    private static final int ADDRESS_SKIP_BYTES_LENGTH = 12;
    private static final int FUNCTION_SELECTOR_BYTES_LENGTH = 4;

    public static final List<NftExchange> NO_NFT_EXCHANGES = Collections.emptyList();
    public static final List<FungibleTokenTransfer> NO_FUNGIBLE_TRANSFERS = Collections.emptyList();

    /* --- Token Create Structs --- */
    private static final String KEY_VALUE_DECODER = "(bool,bytes32,bytes,bytes,bytes32)";
    public static final String TOKEN_KEY_DECODER = "(int32," + KEY_VALUE_DECODER + ")";
    public static final String EXPIRY_DECODER = "(int64,bytes32,int64)";
    public static final String FIXED_FEE_DECODER = "(int64,bytes32,bool,bool,bytes32)";
    public static final String FRACTIONAL_FEE_DECODER = "(int64,int64,int64,int64,bool,bytes32)";
    public static final String ROYALTY_FEE_DECODER = "(int64,int64,int64,bytes32,bool,bytes32)";

    public static final String HEDERA_TOKEN_STRUCT =
            "(string,string,address,string,bool,uint32,bool," + TOKEN_KEY + ARRAY_BRACKETS + "," + EXPIRY + ")";
    public static final String HEDERA_TOKEN_STRUCT_V2 =
            "(string,string,address,string,bool,int64,bool," + TOKEN_KEY + ARRAY_BRACKETS + "," + EXPIRY + ")";
    public static final String HEDERA_TOKEN_STRUCT_V3 =
            "(string,string,address,string,bool,int64,bool," + TOKEN_KEY + ARRAY_BRACKETS + "," + EXPIRY_V2 + ")";
    public static final String HEDERA_TOKEN_STRUCT_DECODER = "(string,string,bytes32,string,bool,int64,bool,"
            + TOKEN_KEY_DECODER
            + ARRAY_BRACKETS
            + ","
            + EXPIRY_DECODER
            + ")";

    private DecodingFacade() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static List<TokenKeyWrapper> decodeTokenKeys(
            @NonNull final Tuple[] tokenKeysTuples, final UnaryOperator<byte[]> aliasResolver) {
        final List<TokenKeyWrapper> tokenKeys = new ArrayList<>(tokenKeysTuples.length);
        for (final var tokenKeyTuple : tokenKeysTuples) {
            final var keyType = (int) tokenKeyTuple.get(0);
            final Tuple keyValueTuple = tokenKeyTuple.get(1);
            final var inheritAccountKey = (Boolean) keyValueTuple.get(0);
            final var contractId =
                    EntityIdUtils.asContract(convertLeftPaddedAddressToAccountId(keyValueTuple.get(1), aliasResolver));
            final var ed25519 = (byte[]) keyValueTuple.get(2);
            final var ecdsaSecp256K1 = (byte[]) keyValueTuple.get(3);
            final var delegatableContractId =
                    EntityIdUtils.asContract(convertLeftPaddedAddressToAccountId(keyValueTuple.get(4), aliasResolver));
            tokenKeys.add(new TokenKeyWrapper(
                    keyType,
                    new KeyValueWrapper(
                            inheritAccountKey,
                            contractId.getContractNum() != 0 ? contractId : null,
                            ed25519,
                            ecdsaSecp256K1,
                            delegatableContractId.getContractNum() != 0 ? delegatableContractId : null)));
        }
        return tokenKeys;
    }

    public static TokenExpiryWrapper decodeTokenExpiry(
            @NonNull final Tuple expiryTuple, final UnaryOperator<byte[]> aliasResolver) {
        final var second = (long) expiryTuple.get(0);
        final var autoRenewAccount = convertLeftPaddedAddressToAccountId(expiryTuple.get(1), aliasResolver);
        final var autoRenewPeriod = (long) expiryTuple.get(2);
        return new TokenExpiryWrapper(
                second, autoRenewAccount.getAccountNum() == 0 ? null : autoRenewAccount, autoRenewPeriod);
    }

    public static Tuple decodeFunctionCall(
            @NonNull final Bytes input, final Bytes selector, final ABIType<Tuple> decoder) {
        if (!selector.equals(input.slice(0, FUNCTION_SELECTOR_BYTES_LENGTH))) {
            throw new IllegalArgumentException("Selector does not match, expected "
                    + selector
                    + " actual "
                    + input.slice(0, FUNCTION_SELECTOR_BYTES_LENGTH));
        }
        return decoder.decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH).toArray());
    }

    public static List<AccountID> decodeAccountIds(
            @NonNull final byte[][] accountBytesArray, final UnaryOperator<byte[]> aliasResolver) {
        final List<AccountID> accountIDs = new ArrayList<>();
        for (final var account : accountBytesArray) {
            accountIDs.add(convertLeftPaddedAddressToAccountId(account, aliasResolver));
        }
        return accountIDs;
    }

    public static AccountID convertLeftPaddedAddressToAccountId(
            final byte[] leftPaddedAddress, @NonNull final UnaryOperator<byte[]> aliasResolver) {
        final var addressOrAlias = Arrays.copyOfRange(leftPaddedAddress, ADDRESS_SKIP_BYTES_LENGTH, WORD_LENGTH);
        return accountIdFromEvmAddress(aliasResolver.apply(addressOrAlias));
    }
}
