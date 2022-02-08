package com.hedera.mirror.common.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Range;

public interface History {

    Range<Long> getTimestampRange();

    void setTimestampRange(Range<Long> timestampRange);

    @JsonIgnore
    default Long getTimestampLower() {
        var timestampRange = getTimestampRange();
        return timestampRange != null && timestampRange.hasLowerBound() ? timestampRange.lowerEndpoint() : null;
    }

    default void setTimestampLower(long timestampLower) {
        setTimestampRange(Range.atLeast(timestampLower));
    }

    @JsonIgnore
    default Long getTimestampUpper() {
        var timestampRange = getTimestampRange();
        return timestampRange != null && timestampRange.hasUpperBound() ? timestampRange.upperEndpoint() : null;
    }

    default void setTimestampUpper(long timestampUpper) {
        setTimestampRange(Range.closedOpen(getTimestampLower(), timestampUpper));
    }
}
