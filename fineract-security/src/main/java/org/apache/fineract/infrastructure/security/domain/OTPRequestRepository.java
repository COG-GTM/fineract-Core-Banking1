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
package org.apache.fineract.infrastructure.security.domain;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.data.OTPRequest;
import org.apache.fineract.infrastructure.security.exception.OTPRequestTooFrequentException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

@Repository
@ConditionalOnProperty("fineract.security.2fa.enabled")
@SuppressWarnings({ "MemberName" })
public class OTPRequestRepository {

    private static final ConcurrentHashMap<String, OTPRequest> OTT_REQUESTS = new ConcurrentHashMap<>();

    public OTPRequest getOTPRequestForUser(AppUser user) {
        Assert.notNull(user, "User must not be null");

        return OTT_REQUESTS.get(keyFor(user));
    }

    /**
     * Stores {@code request} for the user, replacing the current request unless that one was created less than
     * {@code cooldownSeconds} ago.
     *
     * @return the replaced request, or {@code null} if there was none
     * @throws OTPRequestTooFrequentException
     *             if the current request is still within the cooldown
     */
    public OTPRequest replaceOTPRequest(AppUser user, OTPRequest request, int cooldownSeconds) {
        Assert.notNull(user, "User must not be null");
        Assert.notNull(request, "Request must not be null");

        final AtomicBoolean added = new AtomicBoolean(false);
        final AtomicReference<OTPRequest> replaced = new AtomicReference<>();
        OTT_REQUESTS.compute(keyFor(user), (key, existing) -> {
            if (existing != null && existing.isRequestedWithin(cooldownSeconds)) {
                return existing;
            }
            added.set(true);
            replaced.set(existing);
            return request;
        });
        if (!added.get()) {
            throw new OTPRequestTooFrequentException(cooldownSeconds);
        }
        return replaced.get();
    }

    /**
     * Puts {@code previous} back if {@code request} is still the user's current request, e.g. when delivering it failed.
     */
    public void restoreOTPRequest(AppUser user, OTPRequest request, OTPRequest previous) {
        if (previous == null) {
            OTT_REQUESTS.remove(keyFor(user), request);
        } else {
            OTT_REQUESTS.replace(keyFor(user), request, previous);
        }
    }

    public boolean deleteOTPRequestForUser(AppUser user, OTPRequest request) {
        return OTT_REQUESTS.remove(keyFor(user), request);
    }

    private static String keyFor(AppUser user) {
        return ThreadLocalContextUtil.getTenant().getTenantIdentifier() + ":" + user.getId();
    }
}
