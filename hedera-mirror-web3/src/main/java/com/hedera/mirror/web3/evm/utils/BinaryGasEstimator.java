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
package com.hedera.mirror.web3.evm.utils;

import java.util.function.BiConsumer;
import java.util.function.LongFunction;

import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;

public class BinaryGasEstimator {
    public static long search(final BiConsumer<HederaEvmTransactionProcessingResult, Integer> metricUpdater,
                        final LongFunction<HederaEvmTransactionProcessingResult> call, long lo, long hi) {
        long prevGasLimit = lo;
        int iterationsMade = 0;

        do {
            long mid = (hi + lo) / 2;
            HederaEvmTransactionProcessingResult transactionResult = call.apply(mid);

            metricUpdater.accept(transactionResult, ++iterationsMade);
            boolean err = !transactionResult.isSuccessful() || transactionResult.getGasUsed() < 0;
            long gasUsed = err ? prevGasLimit : transactionResult.getGasUsed();
            if (err || gasUsed == 0) {
                lo = mid;
            } else {
                hi = mid;
                if (Math.abs(prevGasLimit - mid) < 1200L) {
                    lo = hi;
                }
            }
            prevGasLimit = mid;
        } while (lo + 1 < hi);

        return hi;
    }
}
