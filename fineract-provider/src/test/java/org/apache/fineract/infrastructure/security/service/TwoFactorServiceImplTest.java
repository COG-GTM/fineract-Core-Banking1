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
package org.apache.fineract.infrastructure.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.PlatformEmailService;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.constants.TwoFactorConstants;
import org.apache.fineract.infrastructure.security.data.OTPRequest;
import org.apache.fineract.infrastructure.security.domain.OTPRequestRepository;
import org.apache.fineract.infrastructure.security.domain.TFAccessToken;
import org.apache.fineract.infrastructure.security.domain.TFAccessTokenRepository;
import org.apache.fineract.infrastructure.security.exception.OTPRequestTooFrequentException;
import org.apache.fineract.infrastructure.security.exception.OTPTokenInvalidException;
import org.apache.fineract.infrastructure.sms.domain.SmsMessageRepository;
import org.apache.fineract.infrastructure.sms.scheduler.SmsMessageScheduledJobService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TwoFactorServiceImplTest {

    private static final AtomicLong USER_IDS = new AtomicLong(9_000_000L);

    private PlatformEmailService emailService;
    private TwoFactorServiceImpl twoFactorService;
    private AppUser user;
    private String lastSentToken;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));

        AccessTokenGenerationService accessTokenGenerationService = mock(AccessTokenGenerationService.class);
        when(accessTokenGenerationService.generateRandomToken()).thenReturn("access-token");
        emailService = mock(PlatformEmailService.class);

        TwoFactorConfigurationService configurationService = mock(TwoFactorConfigurationService.class);
        when(configurationService.isEmailEnabled()).thenReturn(true);
        when(configurationService.getOTPTokenLength()).thenReturn(5);
        when(configurationService.getOTPTokenLiveTime()).thenReturn(300);
        when(configurationService.getAccessTokenLiveTime()).thenReturn(86400);
        when(configurationService.getFormattedEmailSubjectFor(any(), any())).thenReturn("subject");
        when(configurationService.getFormattedEmailBodyFor(any(), any())).thenAnswer(invocation -> {
            lastSentToken = invocation.<OTPRequest>getArgument(1).getToken();
            return lastSentToken;
        });

        twoFactorService = new TwoFactorServiceImpl(accessTokenGenerationService, emailService, mock(SmsMessageScheduledJobService.class),
                new OTPRequestRepository(), mock(TFAccessTokenRepository.class), mock(SmsMessageRepository.class), configurationService);

        user = mock(AppUser.class);
        when(user.getId()).thenReturn(USER_IDS.incrementAndGet());
        when(user.getEmail()).thenReturn("alice@example.com");
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void correctTokenIssuesAccessTokenOnlyOnce() {
        requestToken();
        final String otp = lastSentToken;

        TFAccessToken accessToken = twoFactorService.createAccessTokenFromOTP(user, otp.toLowerCase());

        assertNotNull(accessToken);
        assertThrows(OTPTokenInvalidException.class, () -> twoFactorService.createAccessTokenFromOTP(user, otp));
    }

    @Test
    void correctTokenIsAcceptedAfterFewerFailuresThanTheLimit() {
        requestToken();
        for (int i = 0; i < TwoFactorServiceImpl.MAX_OTP_VALIDATION_ATTEMPTS - 1; i++) {
            assertThrows(OTPTokenInvalidException.class, () -> twoFactorService.createAccessTokenFromOTP(user, wrongToken()));
        }

        assertNotNull(twoFactorService.createAccessTokenFromOTP(user, lastSentToken));
    }

    @Test
    void correctTokenIsRejectedOnceFailedAttemptsReachTheLimit() {
        requestToken();
        for (int i = 0; i < TwoFactorServiceImpl.MAX_OTP_VALIDATION_ATTEMPTS; i++) {
            assertThrows(OTPTokenInvalidException.class, () -> twoFactorService.createAccessTokenFromOTP(user, wrongToken()));
        }

        assertThrows(OTPTokenInvalidException.class, () -> twoFactorService.createAccessTokenFromOTP(user, lastSentToken));
    }

    @Test
    void newTokenCannotBeRequestedDuringCooldown() {
        requestToken();

        assertThrows(OTPRequestTooFrequentException.class, this::requestToken);
        verify(emailService, times(1)).sendDefinedEmail(any(EmailDetail.class));
    }

    @Test
    void lockedOutTokenCannotBeReplacedDuringCooldown() {
        requestToken();
        for (int i = 0; i < TwoFactorServiceImpl.MAX_OTP_VALIDATION_ATTEMPTS; i++) {
            assertThrows(OTPTokenInvalidException.class, () -> twoFactorService.createAccessTokenFromOTP(user, wrongToken()));
        }

        assertThrows(OTPRequestTooFrequentException.class, this::requestToken);
    }

    @Test
    void newTokenCanBeRequestedRightAfterSuccessfulValidation() {
        requestToken();
        twoFactorService.createAccessTokenFromOTP(user, lastSentToken);

        requestToken();
        assertNotNull(twoFactorService.createAccessTokenFromOTP(user, lastSentToken));
    }

    @Test
    void generatedTokensUseAllThirtySixAlphanumericCharacters() {
        String token = new RandomOTPGenerator(20_000).generate();
        Set<Character> characters = new HashSet<>();
        for (char c : token.toCharArray()) {
            characters.add(c);
        }

        assertEquals(36, characters.size());
        assertNotEquals(-1, token.indexOf('W'));
    }

    private void requestToken() {
        twoFactorService.createNewOTPToken(user, TwoFactorConstants.EMAIL_DELIVERY_METHOD_NAME, false);
    }

    private String wrongToken() {
        return lastSentToken.charAt(0) == '0' ? "1" + lastSentToken.substring(1) : "0" + lastSentToken.substring(1);
    }
}
