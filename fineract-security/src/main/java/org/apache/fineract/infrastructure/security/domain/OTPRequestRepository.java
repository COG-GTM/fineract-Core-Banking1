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
import org.apache.fineract.infrastructure.security.data.OTPRequest;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

@Repository
@ConditionalOnProperty("fineract.security.2fa.enabled")
@SuppressWarnings({ "MemberName" })
public class OTPRequestRepository {

    private static final ConcurrentHashMap<Long, OTPRequest> OTT_REQUESTS = new ConcurrentHashMap<>();

    public OTPRequest getOTPRequestForUser(AppUser user) {
        Assert.notNull(user, "User must not be null");

        return OTT_REQUESTS.get(user.getId());
    }

    /**
     * Stores {@code request} for the user unless the user's current request was created less than
     * {@code cooldownSeconds} ago.
     *
     * @return {@code true} if the request was stored
     */
    public boolean addOTPRequestIfNotRequestedWithin(AppUser user, OTPRequest request, int cooldownSeconds) {
        Assert.notNull(user, "User must not be null");
        Assert.notNull(request, "Request must not be null");

        final AtomicBoolean added = new AtomicBoolean(false);
        OTT_REQUESTS.compute(user.getId(), (id, existing) -> {
            if (existing != null && existing.isRequestedWithin(cooldownSeconds)) {
                return existing;
            }
            added.set(true);
            return request;
        });
        return added.get();
    }

    public boolean deleteOTPRequestForUser(AppUser user, OTPRequest request) {
        return OTT_REQUESTS.remove(user.getId(), request);
    }
}
