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
package org.apache.fineract.portfolio.savings.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.UriInfo;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.apache.fineract.portfolio.savings.DepositsApiConstants;
import org.apache.fineract.portfolio.savings.service.DepositAccountPreMatureCalculationPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepositAccountsPrematureAmountAuthorizationTest {

    private static final Long ACCOUNT_ID = 42L;
    private static final String BODY = "{\"closedOnDate\":\"01 January 2024\",\"dateFormat\":\"dd MMMM yyyy\",\"locale\":\"en\"}";
    private static final String COMMAND = "calculatePrematureAmount";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private FromJsonHelper fromJsonHelper;
    @Mock
    private DepositAccountPreMatureCalculationPlatformService accountPreMatureCalculationPlatformService;
    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private RecurringDepositAccountsApiResource recurringDepositAccountsApiResource;
    @InjectMocks
    private FixedDepositAccountsApiResource fixedDepositAccountsApiResource;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void recurringDepositCalculatePrematureAmountRequiresReadPermission() {
        doThrow(new NoAuthorizationException("no READ_RECURRINGDEPOSITACCOUNT")).when(user)
                .validateHasReadPermission(DepositsApiConstants.RECURRING_DEPOSIT_ACCOUNT_RESOURCE_NAME);

        assertThrows(NoAuthorizationException.class,
                () -> recurringDepositAccountsApiResource.handleCommands(ACCOUNT_ID, COMMAND, uriInfo, BODY));

        verifyNoInteractions(accountPreMatureCalculationPlatformService);
    }

    @Test
    void fixedDepositCalculatePrematureAmountRequiresReadPermission() {
        doThrow(new NoAuthorizationException("no READ_FIXEDDEPOSITACCOUNT")).when(user)
                .validateHasReadPermission(DepositsApiConstants.FIXED_DEPOSIT_ACCOUNT_RESOURCE_NAME);

        assertThrows(NoAuthorizationException.class,
                () -> fixedDepositAccountsApiResource.handleCommands(ACCOUNT_ID, COMMAND, uriInfo, BODY));

        verifyNoInteractions(accountPreMatureCalculationPlatformService);
    }

    @Test
    void recurringDepositCalculatePrematureAmountRunsForAuthorizedUser() {
        recurringDepositAccountsApiResource.handleCommands(ACCOUNT_ID, COMMAND, uriInfo, BODY);

        verify(user).validateHasReadPermission(DepositsApiConstants.RECURRING_DEPOSIT_ACCOUNT_RESOURCE_NAME);
        verify(accountPreMatureCalculationPlatformService).calculatePreMatureAmount(eq(ACCOUNT_ID), any(),
                eq(DepositAccountType.RECURRING_DEPOSIT));
    }

    @Test
    void fixedDepositCalculatePrematureAmountRunsForAuthorizedUser() {
        fixedDepositAccountsApiResource.handleCommands(ACCOUNT_ID, COMMAND, uriInfo, BODY);

        verify(user).validateHasReadPermission(DepositsApiConstants.FIXED_DEPOSIT_ACCOUNT_RESOURCE_NAME);
        verify(accountPreMatureCalculationPlatformService).calculatePreMatureAmount(eq(ACCOUNT_ID), any(),
                eq(DepositAccountType.FIXED_DEPOSIT));
    }
}
