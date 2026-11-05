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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.campaigns.sms.service.SmsCampaignDropdownReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.GmailBackedPlatformEmailService;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.sms.domain.SmsMessageRepository;
import org.apache.fineract.infrastructure.sms.scheduler.SmsMessageScheduledJobService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.self.registration.SelfServiceApiConstants;
import org.apache.fineract.portfolio.self.registration.domain.SelfServiceRegistration;
import org.apache.fineract.portfolio.self.registration.domain.SelfServiceRegistrationRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicy;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicyRepository;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.apache.fineract.useradministration.domain.UserDomainService;
import org.apache.fineract.useradministration.service.AppUserReadPlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
public class SelfServiceRegistrationWritePlatformServiceImplTest {

    private static final String RAW_PASSWORD = "Abcdef1#2$3%";
    private static final String TOKEN = "1234";

    @Mock
    private SelfServiceRegistrationRepository selfServiceRegistrationRepository;
    @Mock
    private SelfServiceRegistrationReadPlatformService selfServiceRegistrationReadPlatformService;
    @Mock
    private ClientRepositoryWrapper clientRepository;
    @Mock
    private PasswordValidationPolicyRepository passwordValidationPolicyRepository;
    @Mock
    private UserDomainService userDomainService;
    @Mock
    private GmailBackedPlatformEmailService gmailBackedPlatformEmailService;
    @Mock
    private SmsMessageRepository smsMessageRepository;
    @Mock
    private SmsMessageScheduledJobService smsMessageScheduledJobService;
    @Mock
    private SmsCampaignDropdownReadPlatformService smsCampaignDropdownReadPlatformService;
    @Mock
    private AppUserReadPlatformService appUserReadPlatformService;
    @Mock
    private RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private SelfServiceRegistrationWritePlatformServiceImpl underTest;

    @BeforeEach
    public void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        underTest = new SelfServiceRegistrationWritePlatformServiceImpl(selfServiceRegistrationRepository, new FromJsonHelper(),
                selfServiceRegistrationReadPlatformService, clientRepository, passwordValidationPolicyRepository, userDomainService,
                gmailBackedPlatformEmailService, smsMessageRepository, smsMessageScheduledJobService,
                smsCampaignDropdownReadPlatformService, appUserReadPlatformService, roleRepository, passwordEncoder);
    }

    @AfterEach
    public void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    public void createRegistrationRequestStoresHashedPassword() {
        PasswordValidationPolicy policy = mock(PasswordValidationPolicy.class);
        when(policy.getRegex()).thenReturn(".*");
        when(policy.getDescription()).thenReturn("any");
        when(passwordValidationPolicyRepository.findActivePasswordValidationPolicy()).thenReturn(policy);
        when(appUserReadPlatformService.isUsernameExist("alice")).thenReturn(false);
        when(selfServiceRegistrationReadPlatformService.isClientExist(eq("000000001"), eq("Alice"), eq("Smith"), isNull(), anyBoolean()))
                .thenReturn(true);
        when(clientRepository.getClientByAccountNumber("000000001")).thenReturn(mock(Client.class));

        String json = "{\"accountNumber\":\"000000001\",\"firstName\":\"Alice\",\"lastName\":\"Smith\",\"username\":\"alice\","
                + "\"password\":\"" + RAW_PASSWORD + "\",\"authenticationMode\":\"email\",\"email\":\"alice@example.com\"}";
        underTest.createRegistrationRequest(json);

        ArgumentCaptor<SelfServiceRegistration> captor = ArgumentCaptor.forClass(SelfServiceRegistration.class);
        verify(selfServiceRegistrationRepository).saveAndFlush(captor.capture());
        String stored = captor.getValue().getEncodedPassword();
        assertNotEquals(RAW_PASSWORD, stored);
        assertTrue(stored.startsWith("{bcrypt}"));
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, stored));
    }

    @Test
    public void createUserUsesStoredHashAndClearsItFromRequest() {
        String hash = passwordEncoder.encode(RAW_PASSWORD);
        Client client = mock(Client.class);
        when(client.getOffice()).thenReturn(mock(Office.class));
        when(client.getFirstname()).thenReturn("Alice");
        when(client.getLastname()).thenReturn("Smith");
        SelfServiceRegistration registration = SelfServiceRegistration.instance(client, "000000001", "Alice", "Smith", null,
                "alice@example.com", TOKEN, "alice", hash);
        when(selfServiceRegistrationRepository.getRequestByIdAndAuthenticationToken(1L, TOKEN)).thenReturn(registration);
        when(roleRepository.getRoleByName(SelfServiceApiConstants.SELF_SERVICE_USER_ROLE)).thenReturn(mock(Role.class));

        AppUser created = underTest.createUser("{\"requestId\":1,\"authenticationToken\":\"" + TOKEN + "\"}");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userDomainService).createWithEncodedPassword(captor.capture());
        verify(userDomainService, never()).create(any(), any());
        assertEquals(created, captor.getValue());
        assertEquals(hash, created.getPassword());
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, created.getPassword()));
        assertNull(registration.getEncodedPassword());
        verify(selfServiceRegistrationRepository).saveAndFlush(registration);
    }

    @Test
    public void createUserRejectsRequestWithoutStoredPassword() {
        SelfServiceRegistration registration = SelfServiceRegistration.instance(mock(Client.class), "000000001", "Alice", "Smith", null,
                "alice@example.com", TOKEN, "alice", null);
        when(selfServiceRegistrationRepository.getRequestByIdAndAuthenticationToken(1L, TOKEN)).thenReturn(registration);

        assertThrows(PlatformDataIntegrityException.class,
                () -> underTest.createUser("{\"requestId\":1,\"authenticationToken\":\"" + TOKEN + "\"}"));
        verify(userDomainService, never()).createWithEncodedPassword(any());
        verify(userDomainService, never()).create(any(), any());
        verify(selfServiceRegistrationRepository, never()).saveAndFlush(any());
    }
}
