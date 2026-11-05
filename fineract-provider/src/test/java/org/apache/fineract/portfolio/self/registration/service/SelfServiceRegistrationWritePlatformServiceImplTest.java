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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.campaigns.sms.service.SmsCampaignDropdownReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.GmailBackedPlatformEmailService;
import org.apache.fineract.infrastructure.sms.domain.SmsMessageRepository;
import org.apache.fineract.infrastructure.sms.scheduler.SmsMessageScheduledJobService;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.apache.fineract.portfolio.self.registration.domain.SelfServiceRegistration;
import org.apache.fineract.portfolio.self.registration.domain.SelfServiceRegistrationRepository;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicy;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicyRepository;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.apache.fineract.useradministration.domain.UserDomainService;
import org.apache.fineract.useradministration.service.AppUserReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SelfServiceRegistrationWritePlatformServiceImplTest {

    private static final String ACCOUNT_NUMBER = "000000001";
    private static final String STORED_EMAIL = "alice@example.com";

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

    private SelfServiceRegistrationWritePlatformServiceImpl service;
    private Client client;

    @BeforeEach
    void setUp() {
        service = new SelfServiceRegistrationWritePlatformServiceImpl(selfServiceRegistrationRepository, new FromJsonHelper(),
                selfServiceRegistrationReadPlatformService, clientRepository, passwordValidationPolicyRepository, userDomainService,
                gmailBackedPlatformEmailService, smsMessageRepository, smsMessageScheduledJobService,
                smsCampaignDropdownReadPlatformService, appUserReadPlatformService, roleRepository);

        PasswordValidationPolicy policy = mock(PasswordValidationPolicy.class);
        when(policy.getRegex()).thenReturn(".*");
        when(policy.getDescription()).thenReturn("any");
        when(passwordValidationPolicyRepository.findActivePasswordValidationPolicy()).thenReturn(policy);

        client = mock(Client.class);
        when(client.getId()).thenReturn(1L);
        when(client.getEmailAddress()).thenReturn(STORED_EMAIL);
        when(clientRepository.getClientByAccountNumber(ACCOUNT_NUMBER)).thenReturn(client);
    }

    private static String emailRequest(String email) {
        return "{\"accountNumber\":\"" + ACCOUNT_NUMBER + "\",\"firstName\":\"Alice\",\"lastName\":\"Smith\",\"username\":\"alice\","
                + "\"password\":\"Passw0rd!\",\"authenticationMode\":\"email\",\"email\":\"" + email + "\"}";
    }

    @Test
    void emailModeRejectsEmailNotStoredOnClient() {
        when(selfServiceRegistrationReadPlatformService.isClientExist(ACCOUNT_NUMBER, "Alice", "Smith", null, "mallory@example.com", true))
                .thenReturn(false);

        assertThrows(ClientNotFoundException.class, () -> service.createRegistrationRequest(emailRequest("mallory@example.com")));

        verify(selfServiceRegistrationRepository, never()).saveAndFlush(any());
        verify(gmailBackedPlatformEmailService, never()).sendDefinedEmail(any());
    }

    @Test
    void emailModeSendsTokenToStoredEmail() {
        when(selfServiceRegistrationReadPlatformService.isClientExist(eq(ACCOUNT_NUMBER), eq("Alice"), eq("Smith"), eq(null), anyString(),
                eq(true))).thenReturn(true);

        SelfServiceRegistration registration = service.createRegistrationRequest(emailRequest("ALICE@example.com"));

        assertEquals(STORED_EMAIL, registration.getEmail());
        ArgumentCaptor<EmailDetail> captor = ArgumentCaptor.forClass(EmailDetail.class);
        verify(gmailBackedPlatformEmailService).sendDefinedEmail(captor.capture());
        assertEquals(STORED_EMAIL, captor.getValue().getAddress());
    }

    @Test
    void registrationRejectedWhenClientAlreadyHasSelfServiceUser() {
        when(selfServiceRegistrationReadPlatformService.isClientExist(eq(ACCOUNT_NUMBER), eq("Alice"), eq("Smith"), eq(null), anyString(),
                eq(true))).thenReturn(true);
        when(selfServiceRegistrationReadPlatformService.isClientMappedToSelfServiceUser(1L)).thenReturn(true);

        assertThrows(PlatformDataIntegrityException.class, () -> service.createRegistrationRequest(emailRequest(STORED_EMAIL)));

        verify(selfServiceRegistrationRepository, never()).saveAndFlush(any());
        verify(gmailBackedPlatformEmailService, never()).sendDefinedEmail(any());
    }

    @Test
    void createUserRejectedWhenClientAlreadyHasSelfServiceUser() {
        SelfServiceRegistration registration = mock(SelfServiceRegistration.class);
        when(registration.getClient()).thenReturn(client);
        when(selfServiceRegistrationRepository.getRequestByIdAndAuthenticationToken(5L, "1234")).thenReturn(registration);
        when(selfServiceRegistrationReadPlatformService.isClientMappedToSelfServiceUser(1L)).thenReturn(true);

        assertThrows(PlatformDataIntegrityException.class,
                () -> service.createUser("{\"requestId\":5,\"authenticationToken\":\"1234\"}"));

        verify(userDomainService, never()).create(any(), any());
    }
}
