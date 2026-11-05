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
package org.apache.fineract.infrastructure.security.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.apache.fineract.infrastructure.core.service.DateUtils;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class OTPRequest {

    private String token;
    private OTPMetadata metadata;

    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private int failedAttempts;

    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private boolean consumed;

    public static OTPRequest create(String token, int tokenLiveTimeInSec, boolean extendedAccessToken, OTPDeliveryMethod deliveryMethod) {
        final OTPMetadata metadata = new OTPMetadata()
                .setRequestTime(DateUtils.getLocalDateTimeOfTenant().atZone(DateUtils.getDateTimeZoneOfTenant()))
                .setTokenLiveTimeInSec(tokenLiveTimeInSec).setExtendedAccessToken(extendedAccessToken).setDeliveryMethod(deliveryMethod);

        return new OTPRequest().setToken(token).setMetadata(metadata);
    }

    public boolean isValid() {
        ZonedDateTime expireTime = metadata.getRequestTime().plusSeconds(metadata.getTokenLiveTimeInSec());
        return ZonedDateTime.now(DateUtils.getDateTimeZoneOfTenant()).isBefore(expireTime);
    }

    public boolean isRequestedWithin(int seconds) {
        ZonedDateTime cooldownEnd = metadata.getRequestTime().plusSeconds(seconds);
        return ZonedDateTime.now(DateUtils.getDateTimeZoneOfTenant()).isBefore(cooldownEnd);
    }

    /**
     * Checks a submitted token against this request. Every mismatch counts as a failed attempt; once
     * {@code maxFailedAttempts} is reached, or after a successful match, this request never verifies again.
     */
    public synchronized boolean verify(String candidate, int maxFailedAttempts) {
        if (consumed || failedAttempts >= maxFailedAttempts || !isValid()) {
            return false;
        }
        if (candidate != null && tokensMatch(token, candidate)) {
            consumed = true;
            return true;
        }
        failedAttempts++;
        return false;
    }

    private static boolean tokensMatch(String expected, String candidate) {
        return MessageDigest.isEqual(expected.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                candidate.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }
}
