/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.fees.usage.token.meta;

/**
 *  Exact copy from hedera-services
 * /** This is simply to get rid of code duplication with {@link TokenFreezeMeta} class. */
public class TokenUnfreezeMeta extends TokenFreezeMeta {
    public TokenUnfreezeMeta(final int bpt) {
        super(bpt);
    }
}
