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

package com.hedera.mirror.web3.evm.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class StackedStateFramesTest {

    @Test
    void constructionHappyPath() {

        final var accessors = List.<DatabaseAccessor<Integer, ?>>of(
                new BareDatabaseAccessor<Integer, Character>() {}, new BareDatabaseAccessor<Integer, String>() {});

        final var sut = new StackedStateFrames<>(accessors);

        final var softly = new SoftAssertions();
        softly.assertThat(sut.height()).as("visible height").isOne();
        softly.assertThat(sut.cachedFramesDepth()).as("true height").isEqualTo(3);
        softly.assertThat(sut.top()).as("RW on top").isInstanceOf(RWCachingStateFrame.class);
        softly.assertThat(sut.top().getUpstream())
                .as("RO is upstream of RW")
                .containsInstanceOf(ROCachingStateFrame.class);
        softly.assertThat(sut.getValueClasses())
                .as("value classes correct")
                .containsExactlyInAnyOrder(Character.class, String.class);
        softly.assertAll();
        sut.pop();
        assertThatExceptionOfType(EmptyStackException.class)
                .as("cannot pop bare stack")
                .isThrownBy(sut::pop);
    }

    @Test
    void constructWithDuplicatedValueTypesFails() {
        final var accessors = List.<DatabaseAccessor<Integer, ?>>of(
                new BareDatabaseAccessor<Integer, Character>() {},
                new BareDatabaseAccessor<Integer, String>() {},
                new BareDatabaseAccessor<Integer, List<Integer>>() {},
                new BareDatabaseAccessor<Integer, String>() {});

        assertThatIllegalArgumentException().isThrownBy(() -> new StackedStateFrames<>(accessors));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void constructWithDifferingKeyTypesFails() {
        final var accessors = List.<DatabaseAccessor<Integer, ?>>of(
                new BareDatabaseAccessor<Integer, Character>() {}, (BareDatabaseAccessor<Integer, Character>)
                        (BareDatabaseAccessor) new BareDatabaseAccessor<Long, String>() {});

        assertThatIllegalArgumentException().isThrownBy(() -> new StackedStateFrames<>(accessors));
    }

    @Test
    void pushAndPopDoSo() {
        final var accessors = List.<DatabaseAccessor<Integer, ?>>of(new BareDatabaseAccessor<Integer, Character>() {});
        final var sut = new StackedStateFrames<>(accessors);
        final var rwOnTop = sut.top();

        assertThat(sut.height()).isOne();
        sut.push();
        assertThat(sut.height()).isEqualTo(2);
        assertThat(sut.cachedFramesDepth()).isEqualTo(4);
        assertThat(sut.top()).isInstanceOf(RWCachingStateFrame.class);
        assertThat(sut.top().getUpstream()).contains(rwOnTop);
        sut.pop();
        assertThat(sut.height()).isOne();
        assertThat(sut.cachedFramesDepth()).isEqualTo(3);
        assertThat(sut.top()).isEqualTo(rwOnTop);
    }

    @Test
    void resetToBaseDoes() {
        final var accessors = List.<DatabaseAccessor<Integer, ?>>of(new BareDatabaseAccessor<Integer, Character>() {});
        final var sut = new StackedStateFrames<>(accessors);
        final var rwOnTopOfBase = sut.top();

        sut.push();
        sut.push();
        sut.push();
        assertThat(sut.height()).isEqualTo(4);
        assertThat(sut.top()).isInstanceOf(RWCachingStateFrame.class);

        sut.resetToBase();
        assertThat(sut.height()).isOne();
        assertThat(sut.cachedFramesDepth()).isEqualTo(3);
        assertThat(sut.top()).isInstanceOf(rwOnTopOfBase.getClass());
    }

    @Test
    void forcePushOfSpecificFrameWithProperUpstream() {
        final var accessors = List.<DatabaseAccessor<Integer, ?>>of(new BareDatabaseAccessor<Integer, Character>() {});
        final var sut = new StackedStateFrames<>(accessors);
        final var newTos = new RWCachingStateFrame<>(Optional.of(sut.top()), Optional.empty(), Character.class);
        final var actual = sut.push(newTos);
        assertThat(sut.height()).isEqualTo(2);
        assertThat(actual).isEqualTo(newTos);
    }

    @Test
    void forcePushOfSpecificFrameWithBadUpstream() {
        final var accessors = List.<DatabaseAccessor<Integer, ?>>of(new BareDatabaseAccessor<Integer, Character>() {});
        final var sut = new StackedStateFrames<>(accessors);
        final var newTos = new RWCachingStateFrame<>(
                Optional.of(new RWCachingStateFrame<Integer>(Optional.empty(), Optional.empty(), Character.class)),
                Optional.empty(),
                Character.class);
        assertThatIllegalArgumentException().isThrownBy(() -> sut.push(newTos));
    }

    @Test
    void replaceEntireStack() {
        final var accessors = List.<DatabaseAccessor<Integer, ?>>of(new BareDatabaseAccessor<Integer, Character>() {});
        final var sut = new StackedStateFrames<>(accessors);
        final var newStack = new RWCachingStateFrame<Integer>(Optional.empty(), Optional.empty(), Character.class);
        sut.replaceEntireStack(newStack);
        assertThat(sut.height()).isZero();
        assertThat(sut.cachedFramesDepth()).isEqualTo(1);
        assertThat(sut.top()).isEqualTo(newStack);
    }

    static class BareDatabaseAccessor<K, V> extends DatabaseAccessor<K, V> {
        @NonNull
        @Override
        public Optional<V> get(@NonNull final K key) {
            throw new UnsupportedOperationException("BareGroundTruthAccessor.get");
        }
    }
}
