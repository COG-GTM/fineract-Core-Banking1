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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.campaigns.sms.data.SmsProviderData;
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
    private static final Long CLIENT_ID = 1L;
    private static final String STORED_EMAIL = "Alice@Example.com";
    private static final String STORED_MOBILE = "9876543210";

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
    @Mock
    private Client client;

    private SelfServiceRegistrationWritePlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        underTest = new SelfServiceRegistrationWritePlatformServiceImpl(selfServiceRegistrationRepository, new FromJsonHelper(),
                selfServiceRegistrationReadPlatformService, clientRepository, passwordValidationPolicyRepository, userDomainService,
                gmailBackedPlatformEmailService, smsMessageRepository, smsMessageScheduledJobService,
                smsCampaignDropdownReadPlatformService, appUserReadPlatformService, roleRepository);

        PasswordValidationPolicy policy = mock(PasswordValidationPolicy.class);
        when(policy.getRegex()).thenReturn("^.{8,}$");
        when(policy.getDescription()).thenReturn("at least 8 characters");
        when(passwordValidationPolicyRepository.findActivePasswordValidationPolicy()).thenReturn(policy);
        when(appUserReadPlatformService.isUsernameExist(anyString())).thenReturn(false);

        when(client.getId()).thenReturn(CLIENT_ID);
        when(client.getEmailAddress()).thenReturn(STORED_EMAIL);
        when(client.getMobileNo()).thenReturn(STORED_MOBILE);
        when(clientRepository.getClientByAccountNumber(ACCOUNT_NUMBER)).thenReturn(client);
        when(selfServiceRegistrationReadPlatformService.isSelfServiceUserExistForClient(anyLong())).thenReturn(false);
    }

    private static String registrationJson(String authenticationMode, String email, String mobileNumber) {
        String json = "{\"accountNumber\":\"" + ACCOUNT_NUMBER + "\",\"firstName\":\"Alice\",\"lastName\":\"Smith\","
                + "\"username\":\"alice\",\"password\":\"Passw0rd!\",\"authenticationMode\":\"" + authenticationMode + "\","
                + "\"email\":\"" + email + "\"";
        if (mobileNumber != null) {
            json += ",\"mobileNumber\":\"" + mobileNumber + "\"";
        }
        return json + "}";
    }

    @Test
    void emailModeRejectsEmailThatDoesNotBelongToClient() {
        when(selfServiceRegistrationReadPlatformService.isClientExist(ACCOUNT_NUMBER, "Alice", "Smith", null, "mallory@attacker.test",
                true)).thenReturn(false);

        assertThrows(ClientNotFoundException.class,
                () -> underTest.createRegistrationRequest(registrationJson("email", "mallory@attacker.test", null)));

        verify(selfServiceRegistrationReadPlatformService).isClientExist(ACCOUNT_NUMBER, "Alice", "Smith", null, "mallory@attacker.test",
                true);
        verify(selfServiceRegistrationRepository, never()).saveAndFlush(any());
        verify(gmailBackedPlatformEmailService, never()).sendDefinedEmail(any());
    }

    @Test
    void emailModeSendsTokenToEmailStoredOnClient() {
        when(selfServiceRegistrationReadPlatformService.isClientExist(ACCOUNT_NUMBER, "Alice", "Smith", null, "alice@example.com", true))
                .thenReturn(true);

        SelfServiceRegistration registration = underTest.createRegistrationRequest(registrationJson("email", "alice@example.com", null));

        assertEquals(STORED_EMAIL, registration.getEmail());
        verify(selfServiceRegistrationRepository).saveAndFlush(registration);
        ArgumentCaptor<EmailDetail> emailCaptor = ArgumentCaptor.forClass(EmailDetail.class);
        verify(gmailBackedPlatformEmailService).sendDefinedEmail(emailCaptor.capture());
        assertEquals(STORED_EMAIL, emailCaptor.getValue().getAddress());
    }

    @Test
    void mobileModeSendsTokenToMobileNumberStoredOnClient() {
        when(selfServiceRegistrationReadPlatformService.isClientExist(eq(ACCOUNT_NUMBER), eq("Alice"), eq("Smith"), eq(STORED_MOBILE),
                anyString(), eq(false))).thenReturn(true);
        SmsProviderData provider = mock(SmsProviderData.class);
        when(provider.getId()).thenReturn(1L);
        when(smsCampaignDropdownReadPlatformService.retrieveSmsProviders()).thenReturn(List.of(provider));

        SelfServiceRegistration registration = underTest
                .createRegistrationRequest(registrationJson("mobile", "alice@example.com", STORED_MOBILE));

        assertEquals(STORED_MOBILE, registration.getMobileNumber());
        verify(gmailBackedPlatformEmailService, never()).sendDefinedEmail(any());
    }

    @Test
    void registrationRejectedWhenClientAlreadyHasSelfServiceUser() {
        when(selfServiceRegistrationReadPlatformService.isClientExist(anyString(), anyString(), anyString(), any(), anyString(),
                anyBoolean())).thenReturn(true);
        when(selfServiceRegistrationReadPlatformService.isSelfServiceUserExistForClient(CLIENT_ID)).thenReturn(true);

        PlatformDataIntegrityException exception = assertThrows(PlatformDataIntegrityException.class,
                () -> underTest.createRegistrationRequest(registrationJson("email", "alice@example.com", null)));

        assertEquals("error.msg.self.service.user.already.exists.for.client", exception.getGlobalisationMessageCode());
        verify(selfServiceRegistrationRepository, never()).saveAndFlush(any());
        verify(gmailBackedPlatformEmailService, never()).sendDefinedEmail(any());
    }

    @Test
    void createUserRejectedWhenClientAlreadyHasSelfServiceUser() {
        SelfServiceRegistration registration = mock(SelfServiceRegistration.class);
        when(registration.getClient()).thenReturn(client);
        when(registration.getUsername()).thenReturn("alice");
        when(selfServiceRegistrationRepository.getRequestByIdAndAuthenticationToken(5L, "1234")).thenReturn(registration);
        when(selfServiceRegistrationReadPlatformService.isSelfServiceUserExistForClient(CLIENT_ID)).thenReturn(true);

        assertThrows(PlatformDataIntegrityException.class,
                () -> underTest.createUser("{\"requestId\":5,\"authenticationToken\":\"1234\"}"));

        verify(userDomainService, never()).create(any(), anyBoolean());
    }
}
