/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.self.account.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.apache.fineract.portfolio.self.account.exception.BeneficiaryAddAttemptsExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfBeneficiariesTPTAddThrottleTest {

    private MutableClock clock;
    private SelfBeneficiariesTPTAddThrottle throttle;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
        throttle = new SelfBeneficiariesTPTAddThrottle(3, Duration.ofHours(1), 5, Duration.ofHours(24), clock);
    }

    @Test
    void locksOutAfterTooManyFailedAttemptsUntilWindowPasses() {
        for (int i = 0; i < 3; i++) {
            throttle.acquire("t:1");
            throttle.recordFailure("t:1");
        }
        assertThrows(BeneficiaryAddAttemptsExceededException.class, () -> throttle.acquire("t:1"));

        clock.advance(Duration.ofMinutes(61));
        assertDoesNotThrow(() -> throttle.acquire("t:1"));
    }

    @Test
    void capsTotalAttemptsEvenWhenTheyAllSucceed() {
        for (int i = 0; i < 5; i++) {
            throttle.acquire("t:1");
            clock.advance(Duration.ofHours(2));
        }
        assertThrows(BeneficiaryAddAttemptsExceededException.class, () -> throttle.acquire("t:1"));

        clock.advance(Duration.ofHours(15));
        assertDoesNotThrow(() -> throttle.acquire("t:1"));
    }

    @Test
    void rejectedAttemptsDoNotExtendTheLockout() {
        for (int i = 0; i < 3; i++) {
            throttle.acquire("t:1");
            throttle.recordFailure("t:1");
        }
        for (int i = 0; i < 10; i++) {
            assertThrows(BeneficiaryAddAttemptsExceededException.class, () -> throttle.acquire("t:1"));
        }
        clock.advance(Duration.ofMinutes(61));
        assertDoesNotThrow(() -> throttle.acquire("t:1"));
    }

    @Test
    void keysAreIsolated() {
        for (int i = 0; i < 3; i++) {
            throttle.acquire("t:1");
            throttle.recordFailure("t:1");
        }
        assertThrows(BeneficiaryAddAttemptsExceededException.class, () -> throttle.acquire("t:1"));
        assertDoesNotThrow(() -> throttle.acquire("t:2"));
        assertDoesNotThrow(() -> throttle.acquire("other:1"));
    }

    @Test
    void expiredEntriesOfInactiveUsersArePurged() {
        for (int i = 0; i < 100; i++) {
            throttle.acquire("t:" + i);
            throttle.recordFailure("t:" + i);
        }
        assertEquals(200, throttle.trackedKeys());

        clock.advance(Duration.ofHours(25));
        throttle.acquire("t:active");
        assertEquals(1, throttle.trackedKeys());
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
