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
package org.apache.fineract.portfolio.self.savings.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.apache.fineract.portfolio.savings.api.SavingsAccountChargesApiResource;
import org.apache.fineract.portfolio.savings.api.SavingsAccountTransactionsApiResource;
import org.apache.fineract.portfolio.savings.api.SavingsAccountsApiResource;
import org.apache.fineract.portfolio.savings.data.SavingsAccountData;
import org.apache.fineract.portfolio.savings.exception.SavingsAccountNotFoundException;
import org.apache.fineract.portfolio.self.client.service.AppuserClientMapperReadService;
import org.apache.fineract.portfolio.self.savings.data.SelfSavingsDataValidator;
import org.apache.fineract.portfolio.self.savings.service.AppuserSavingsMapperReadService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SelfSavingsApiResourceTest {

    private static final Long USER_ID = 7L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long OWN_CLIENT_ID = 1L;
    private static final Long OTHER_CLIENT_ID = 2L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private SavingsAccountsApiResource savingsAccountsApiResource;
    @Mock
    private SavingsAccountChargesApiResource savingsAccountChargesApiResource;
    @Mock
    private SavingsAccountTransactionsApiResource savingsAccountTransactionsApiResource;
    @Mock
    private AppuserSavingsMapperReadService appuserSavingsMapperReadService;
    @Mock
    private AppuserClientMapperReadService appUserClientMapperReadService;
    @Mock
    private DefaultToApiJsonSerializer<SavingsAccountData> toApiJsonSerializer;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private AppUser user;

    private SelfSavingsApiResource resource;

    @BeforeEach
    public void setUp() {
        resource = new SelfSavingsApiResource(context, savingsAccountsApiResource, savingsAccountChargesApiResource,
                savingsAccountTransactionsApiResource, appuserSavingsMapperReadService, new SelfSavingsDataValidator(new FromJsonHelper()),
                appUserClientMapperReadService, toApiJsonSerializer, apiRequestParameterHelper);
        when(context.authenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(appuserSavingsMapperReadService.isSavingsMappedToUser(ACCOUNT_ID, USER_ID)).thenReturn(true);
        when(appUserClientMapperReadService.isClientMappedToUser(OWN_CLIENT_ID, USER_ID)).thenReturn(true);
        when(appUserClientMapperReadService.isClientMappedToUser(OTHER_CLIENT_ID, USER_ID)).thenReturn(false);
        when(savingsAccountsApiResource.update(anyLong(), anyString(), any())).thenReturn("updated");
        when(savingsAccountsApiResource.submitApplication(anyString())).thenReturn("submitted");
    }

    @Test
    public void modifyApplicationWithOwnClientIsDelegated() {
        final String body = "{\"clientId\": 1, \"nominalAnnualInterestRate\": 5}";

        assertEquals("updated", resource.modifySavingsAccountApplication(ACCOUNT_ID, null, body));
        verify(savingsAccountsApiResource).update(ACCOUNT_ID, body, null);
    }

    @Test
    public void modifyApplicationWithUnmappedClientIsRejected() {
        final String body = "{\"clientId\": 2}";

        assertThrows(ClientNotFoundException.class, () -> resource.modifySavingsAccountApplication(ACCOUNT_ID, null, body));
        verify(savingsAccountsApiResource, never()).update(anyLong(), anyString(), any());
    }

    @Test
    public void modifyApplicationWithGroupIdIsRejected() {
        final String body = "{\"clientId\": 1, \"groupId\": 3}";

        assertThrows(UnsupportedParameterException.class, () -> resource.modifySavingsAccountApplication(ACCOUNT_ID, null, body));
        verify(savingsAccountsApiResource, never()).update(anyLong(), anyString(), any());
    }

    @Test
    public void modifyUnmappedAccountIsRejected() {
        final String body = "{\"clientId\": 1}";

        assertThrows(SavingsAccountNotFoundException.class, () -> resource.modifySavingsAccountApplication(99L, null, body));
        verify(savingsAccountsApiResource, never()).update(anyLong(), anyString(), any());
    }

    @Test
    public void submitApplicationWithGroupIdIsRejected() {
        final String body = "{\"clientId\": 1, \"groupId\": 3}";

        assertThrows(UnsupportedParameterException.class, () -> resource.submitSavingsAccountApplication(null, null, body));
        verify(savingsAccountsApiResource, never()).submitApplication(anyString());
    }

    @Test
    public void submitApplicationWithOwnClientIsDelegated() {
        final String body = "{\"clientId\": 1}";

        assertEquals("submitted", resource.submitSavingsAccountApplication(null, null, body));
    }
}
