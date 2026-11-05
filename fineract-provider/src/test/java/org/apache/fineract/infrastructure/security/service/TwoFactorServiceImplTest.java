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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.data.OTPRequest;
import org.apache.fineract.infrastructure.security.domain.OTPRequestRepository;
import org.apache.fineract.infrastructure.security.domain.TFAccessToken;
import org.apache.fineract.infrastructure.security.domain.TFAccessTokenRepository;
import org.apache.fineract.infrastructure.security.exception.OTPTokenInvalidException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceImplTest {

    private static final FineractPlatformTenant TENANT_A = new FineractPlatformTenant(1L, "acme", "Acme", "UTC", null);
    private static final FineractPlatformTenant TENANT_B = new FineractPlatformTenant(2L, "globex", "Globex", "UTC", null);

    @Mock
    private AccessTokenGenerationService accessTokenGenerationService;

    @Mock
    private TFAccessTokenRepository tfAccessTokenRepository;

    @Mock
    private TwoFactorConfigurationService configurationService;

    private final OTPRequestRepository otpRequestRepository = new OTPRequestRepository();

    private TwoFactorServiceImpl twoFactorService;

    @BeforeEach
    void setUp() {
        twoFactorService = new TwoFactorServiceImpl(accessTokenGenerationService, null, null, otpRequestRepository, tfAccessTokenRepository,
                null, configurationService);
        lenient().when(accessTokenGenerationService.generateRandomToken()).thenReturn("access-token");
        lenient().when(configurationService.getAccessTokenLiveTime()).thenReturn(3600);
    }

    @AfterEach
    void tearDown() {
        for (FineractPlatformTenant tenant : new FineractPlatformTenant[] { TENANT_A, TENANT_B }) {
            ThreadLocalContextUtil.setTenant(tenant);
            otpRequestRepository.deleteOTPRequestForUser(userWithId(1L));
        }
        ThreadLocalContextUtil.reset();
    }

    @Test
    void otpIssuedInOneTenantIsRejectedForSameUserIdInAnotherTenant() {
        ThreadLocalContextUtil.setTenant(TENANT_B);
        otpRequestRepository.addOTPRequest(userWithId(1L), OTPRequest.create("MALLORY1", 300, false, null));

        ThreadLocalContextUtil.setTenant(TENANT_A);
        AppUser alice = userWithId(1L);
        assertNull(otpRequestRepository.getOTPRequestForUser(alice));
        assertThrows(OTPTokenInvalidException.class, () -> twoFactorService.createAccessTokenFromOTP(alice, "MALLORY1"));
    }

    @Test
    void otpRequestInOneTenantDoesNotOverwritePendingOtpInAnotherTenant() {
        ThreadLocalContextUtil.setTenant(TENANT_A);
        OTPRequest aliceRequest = OTPRequest.create("ALICE123", 300, false, null);
        otpRequestRepository.addOTPRequest(userWithId(1L), aliceRequest);

        ThreadLocalContextUtil.setTenant(TENANT_B);
        otpRequestRepository.addOTPRequest(userWithId(1L), OTPRequest.create("MALLORY1", 300, false, null));

        ThreadLocalContextUtil.setTenant(TENANT_A);
        assertSame(aliceRequest, otpRequestRepository.getOTPRequestForUser(userWithId(1L)));
        TFAccessToken token = twoFactorService.createAccessTokenFromOTP(userWithId(1L), "alice123");
        assertNotNull(token);
        assertNull(otpRequestRepository.getOTPRequestForUser(userWithId(1L)));
    }

    @Test
    void wrongOrMissingOtpIsRejected() {
        ThreadLocalContextUtil.setTenant(TENANT_A);
        otpRequestRepository.addOTPRequest(userWithId(1L), OTPRequest.create("ALICE123", 300, false, null));

        assertThrows(OTPTokenInvalidException.class, () -> twoFactorService.createAccessTokenFromOTP(userWithId(1L), "ALICE124"));
        assertThrows(OTPTokenInvalidException.class, () -> twoFactorService.createAccessTokenFromOTP(userWithId(1L), null));
    }

    @Test
    void otpComparisonIsAsciiCaseInsensitiveOnly() {
        ThreadLocalContextUtil.setTenant(TENANT_A);
        otpRequestRepository.addOTPRequest(userWithId(1L), OTPRequest.create("PASS12", 300, false, null));

        assertThrows(OTPTokenInvalidException.class, () -> twoFactorService.createAccessTokenFromOTP(userWithId(1L), "PAß12"));
        assertNotNull(twoFactorService.createAccessTokenFromOTP(userWithId(1L), "pass12"));
    }

    private static AppUser userWithId(Long id) {
        AppUser user = mock(AppUser.class);
        lenient().when(user.getId()).thenReturn(id);
        return user;
    }
}
