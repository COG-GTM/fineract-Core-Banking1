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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.portfolio.self.account.exception.BeneficiaryAddAttemptsExceededException;

/**
 * Per self-service user throttle for third party transfer beneficiary registration. Every lookup of a foreign account
 * number is an existence oracle, so both failed lookups and total attempts are capped within sliding windows.
 */
public class SelfBeneficiariesTPTAddThrottle {

    public static final int DEFAULT_MAX_FAILED_ATTEMPTS = 5;
    public static final Duration DEFAULT_FAILED_ATTEMPTS_WINDOW = Duration.ofHours(1);
    public static final int DEFAULT_MAX_ATTEMPTS = 10;
    public static final Duration DEFAULT_ATTEMPTS_WINDOW = Duration.ofHours(24);

    private final int maxFailedAttempts;
    private final Duration failedAttemptsWindow;
    private final int maxAttempts;
    private final Duration attemptsWindow;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failedAttempts = new HashMap<>();
    private final Map<String, Deque<Instant>> attempts = new HashMap<>();
    private Instant nextSweep = Instant.MIN;

    public SelfBeneficiariesTPTAddThrottle() {
        this(DEFAULT_MAX_FAILED_ATTEMPTS, DEFAULT_FAILED_ATTEMPTS_WINDOW, DEFAULT_MAX_ATTEMPTS, DEFAULT_ATTEMPTS_WINDOW, Clock.systemUTC());
    }

    public SelfBeneficiariesTPTAddThrottle(final int maxFailedAttempts, final Duration failedAttemptsWindow, final int maxAttempts,
            final Duration attemptsWindow, final Clock clock) {
        this.maxFailedAttempts = maxFailedAttempts;
        this.failedAttemptsWindow = failedAttemptsWindow;
        this.maxAttempts = maxAttempts;
        this.attemptsWindow = attemptsWindow;
        this.clock = clock;
    }

    /**
     * Records a registration attempt for the given key, or throws if the key is currently locked out.
     */
    public synchronized void acquire(final String key) {
        final Instant now = this.clock.instant();
        sweepExpired(now);
        if (count(this.failedAttempts, key, now.minus(this.failedAttemptsWindow)) >= this.maxFailedAttempts
                || count(this.attempts, key, now.minus(this.attemptsWindow)) >= this.maxAttempts) {
            throw new BeneficiaryAddAttemptsExceededException();
        }
        this.attempts.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(now);
    }

    public synchronized void recordFailure(final String key) {
        this.failedAttempts.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(this.clock.instant());
    }

    synchronized int trackedKeys() {
        return this.failedAttempts.size() + this.attempts.size();
    }

    private void sweepExpired(final Instant now) {
        if (now.isBefore(this.nextSweep)) {
            return;
        }
        final Instant failedWindowStart = now.minus(this.failedAttemptsWindow);
        final Instant attemptsWindowStart = now.minus(this.attemptsWindow);
        List.copyOf(this.failedAttempts.keySet()).forEach(key -> count(this.failedAttempts, key, failedWindowStart));
        List.copyOf(this.attempts.keySet()).forEach(key -> count(this.attempts, key, attemptsWindowStart));
        this.nextSweep = now.plus(this.failedAttemptsWindow);
    }

    private static int count(final Map<String, Deque<Instant>> store, final String key, final Instant windowStart) {
        final Deque<Instant> timestamps = store.get(key);
        if (timestamps == null) {
            return 0;
        }
        while (!timestamps.isEmpty() && !timestamps.peekFirst().isAfter(windowStart)) {
            timestamps.pollFirst();
        }
        if (timestamps.isEmpty()) {
            store.remove(key);
            return 0;
        }
        return timestamps.size();
    }
}
