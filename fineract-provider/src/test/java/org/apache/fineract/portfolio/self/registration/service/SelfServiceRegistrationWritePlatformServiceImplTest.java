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
package org.apache.fineract.portfolio.self.registration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.self.registration.SelfServiceApiConstants;
import org.apache.fineract.portfolio.self.registration.domain.SelfServiceRegistration;
import org.apache.fineract.portfolio.self.registration.domain.SelfServiceRegistrationRepository;
import org.apache.fineract.portfolio.self.registration.exception.SelfServiceRegistrationNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.apache.fineract.useradministration.domain.UserDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelfServiceRegistrationWritePlatformServiceImplTest {

    private static final long REQUEST_ID = 42L;
    private static final String VALID_CODE = SelfServiceRegistrationWritePlatformServiceImpl.randomAuthorizationTokenGeneration();

    private SelfServiceRegistrationRepository repository;
    private UserDomainService userDomainService;
    private RoleRepository roleRepository;
    private SelfServiceRegistrationWritePlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        repository = mock(SelfServiceRegistrationRepository.class);
        userDomainService = mock(UserDomainService.class);
        roleRepository = mock(RoleRepository.class);
        service = new SelfServiceRegistrationWritePlatformServiceImpl(repository, new FromJsonHelper(), null, null, null, userDomainService,
                null, null, null, null, null, roleRepository);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void generatedTokenIsLongAndFromAlphabet() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = SelfServiceRegistrationWritePlatformServiceImpl.randomAuthorizationTokenGeneration();
            assertEquals(SelfServiceApiConstants.AUTHENTICATION_TOKEN_LENGTH, token.length());
            assertTrue(token.chars().allMatch(c -> SelfServiceApiConstants.AUTHENTICATION_TOKEN_ALPHABET.indexOf(c) >= 0));
            tokens.add(token);
        }
        assertEquals(1000, tokens.size());
    }

    @Test
    void tokenHashIsStableNormalizedAndNotPlaintext() {
        String hash = SelfServiceRegistrationWritePlatformServiceImpl.hashAuthorizationToken(VALID_CODE);
        assertEquals(64, hash.length());
        assertNotEquals(VALID_CODE, hash);
        assertEquals(hash, SelfServiceRegistrationWritePlatformServiceImpl.hashAuthorizationToken(" " + VALID_CODE.toLowerCase(Locale.ROOT) + " "));
    }

    @Test
    void rejectsWhenNoAttemptsRemain() {
        when(repository.reserveVerificationAttempt(REQUEST_ID, SelfServiceApiConstants.MAX_AUTHENTICATION_TOKEN_ATTEMPTS)).thenReturn(0);

        assertThrows(SelfServiceRegistrationNotFoundException.class, () -> service.createUser(body(VALID_CODE)));

        verify(repository, never()).findById(anyLong());
        verify(userDomainService, never()).create(any(), any());
    }

    @Test
    void rejectsWrongToken() {
        stubPendingRequest(DateUtils.getLocalDateTimeOfSystem());

        assertThrows(SelfServiceRegistrationNotFoundException.class, () -> service.createUser(body("ZZZZZZZZZZ")));

        verify(repository).reserveVerificationAttempt(REQUEST_ID, SelfServiceApiConstants.MAX_AUTHENTICATION_TOKEN_ATTEMPTS);
        verify(userDomainService, never()).create(any(), any());
        verify(repository, never()).invalidateAuthenticationToken(anyLong());
        verify(repository).invalidateExhaustedAuthenticationToken(REQUEST_ID, SelfServiceApiConstants.MAX_AUTHENTICATION_TOKEN_ATTEMPTS);
    }

    @Test
    void rejectsAndInvalidatesExpiredToken() {
        stubPendingRequest(DateUtils.getLocalDateTimeOfSystem()
                .minusMinutes(SelfServiceApiConstants.AUTHENTICATION_TOKEN_VALIDITY_MINUTES).minusSeconds(1));

        assertThrows(SelfServiceRegistrationNotFoundException.class, () -> service.createUser(body(VALID_CODE)));

        verify(repository).invalidateAuthenticationToken(REQUEST_ID);
        verify(userDomainService, never()).create(any(), any());
    }

    @Test
    void rejectsAlreadyUsedRequest() {
        SelfServiceRegistration registration = stubPendingRequest(DateUtils.getLocalDateTimeOfSystem());
        when(registration.getAuthenticationToken()).thenReturn(null);

        assertThrows(SelfServiceRegistrationNotFoundException.class, () -> service.createUser(body(VALID_CODE)));

        verify(userDomainService, never()).create(any(), any());
    }

    @Test
    void createsUserAndConsumesTokenOnMatch() {
        SelfServiceRegistration registration = stubPendingRequest(DateUtils.getLocalDateTimeOfSystem().minusMinutes(1));
        Client client = mock(Client.class);
        when(client.getOffice()).thenReturn(mock(Office.class));
        when(client.getFirstname()).thenReturn("Alice");
        when(client.getLastname()).thenReturn("Doe");
        when(registration.getClient()).thenReturn(client);
        when(registration.getUsername()).thenReturn("alice");
        when(registration.getPassword()).thenReturn("Password@123");
        when(registration.getEmail()).thenReturn("alice@example.com");
        when(roleRepository.getRoleByName(SelfServiceApiConstants.SELF_SERVICE_USER_ROLE)).thenReturn(mock(Role.class));

        AppUser user = service.createUser(body(VALID_CODE.toLowerCase(Locale.ROOT)));

        assertEquals("alice", user.getUsername());
        verify(userDomainService).create(eq(user), eq(true));
        verify(repository).invalidateAuthenticationToken(REQUEST_ID);
    }

    private SelfServiceRegistration stubPendingRequest(LocalDateTime createdDate) {
        SelfServiceRegistration registration = mock(SelfServiceRegistration.class);
        when(registration.getAuthenticationToken()).thenReturn(SelfServiceRegistrationWritePlatformServiceImpl.hashAuthorizationToken(VALID_CODE));
        when(registration.getCreatedDate()).thenReturn(createdDate);
        when(repository.reserveVerificationAttempt(REQUEST_ID, SelfServiceApiConstants.MAX_AUTHENTICATION_TOKEN_ATTEMPTS)).thenReturn(1);
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.of(registration));
        return registration;
    }

    private static String body(String token) {
        return "{\"requestId\": " + REQUEST_ID + ", \"authenticationToken\": \"" + token + "\"}";
    }
}
