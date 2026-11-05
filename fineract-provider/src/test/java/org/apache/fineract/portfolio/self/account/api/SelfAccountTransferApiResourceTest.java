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
package org.apache.fineract.portfolio.self.account.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.account.api.AccountTransfersApiResource;
import org.apache.fineract.portfolio.account.data.request.AccountTransferRequest;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.self.account.data.SelfAccountTemplateData;
import org.apache.fineract.portfolio.self.account.data.SelfAccountTransferData;
import org.apache.fineract.portfolio.self.account.data.SelfAccountTransferDataValidator;
import org.apache.fineract.portfolio.self.account.exception.DailyTPTTransactionAmountLimitExceededException;
import org.apache.fineract.portfolio.self.account.service.SelfBeneficiariesTPTReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SelfAccountTransferApiResourceTest {

    private static final Long FROM_ACCOUNT_ID = 11L;
    private static final Integer SAVINGS = 2;
    private static final Long TO_ACCOUNT_ID = 22L;
    private static final Long USER_ID = 5L;
    private static final LocalDate TRANSFER_DATE = LocalDate.of(2024, 3, 15);
    private static final long DAILY_LIMIT = 1000L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private DefaultToApiJsonSerializer<SelfAccountTransferData> toApiJsonSerializer;
    @Mock
    private AccountTransfersApiResource accountTransfersApiResource;
    @Mock
    private SelfAccountTransferDataValidator dataValidator;
    @Mock
    private SelfBeneficiariesTPTReadPlatformService tptBeneficiaryReadPlatformService;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private AccountTransfersReadPlatformService accountTransfersReadPlatformService;
    @Mock
    private AppUser user;

    @InjectMocks
    private SelfAccountTransferApiResource resource;

    private final AccountTransferRequest request = new AccountTransferRequest();

    @BeforeEach
    void setUp() {
        when(toApiJsonSerializer.serialize(any())).thenReturn("{}");
        when(context.authenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(tptBeneficiaryReadPlatformService.getTransferLimit(USER_ID, TO_ACCOUNT_ID, SAVINGS)).thenReturn(null);
    }

    @Test
    void firstTransferOfTheDayAboveDailyLimitIsRejected() {
        givenTransferOf("1001");
        givenDailyLimitEnabled();
        givenExistingDailyTotal(null);

        assertThrows(DailyTPTTransactionAmountLimitExceededException.class, () -> resource.create("tpt", request));
        verify(accountTransfersApiResource, never()).create(any());
    }

    @Test
    void firstTransferOfTheDayEqualToDailyLimitIsAllowed() {
        givenTransferOf("1000");
        givenDailyLimitEnabled();
        givenExistingDailyTotal(null);

        assertForwarded();
    }

    @Test
    void transferAboveDailyLimitIsRejectedWhenExistingTotalIsZero() {
        givenTransferOf("5000");
        givenDailyLimitEnabled();
        givenExistingDailyTotal(BigDecimal.ZERO);

        assertThrows(DailyTPTTransactionAmountLimitExceededException.class, () -> resource.create("tpt", request));
        verify(accountTransfersApiResource, never()).create(any());
    }

    @Test
    void transferPushingDailyTotalAboveLimitIsRejected() {
        givenTransferOf("400.01");
        givenDailyLimitEnabled();
        givenExistingDailyTotal(new BigDecimal("600"));

        assertThrows(DailyTPTTransactionAmountLimitExceededException.class, () -> resource.create("tpt", request));
        verify(accountTransfersApiResource, never()).create(any());
    }

    @Test
    void transferReachingExactlyTheDailyLimitIsAllowed() {
        givenTransferOf("400");
        givenDailyLimitEnabled();
        givenExistingDailyTotal(new BigDecimal("600"));

        assertForwarded();
    }

    @Test
    void dailyLimitIsNotCheckedWhenDisabled() {
        givenTransferOf("50000");
        when(configurationDomainService.isDailyTPTLimitEnabled()).thenReturn(false);

        assertForwarded();
        verifyNoInteractions(accountTransfersReadPlatformService);
    }

    private void givenTransferOf(String amount) {
        Map<String, Object> params = new HashMap<>();
        params.put("fromAccount", new SelfAccountTemplateData(FROM_ACCOUNT_ID, SAVINGS, 1L, 1L));
        params.put("toAccount", new SelfAccountTemplateData(TO_ACCOUNT_ID, SAVINGS, 2L, 1L));
        params.put("transactionDate", TRANSFER_DATE);
        params.put("transactionAmount", new BigDecimal(amount));
        when(dataValidator.validateCreate(anyString(), anyString())).thenReturn(params);
    }

    private void givenDailyLimitEnabled() {
        when(configurationDomainService.isDailyTPTLimitEnabled()).thenReturn(true);
        when(configurationDomainService.getDailyTPTLimit()).thenReturn(DAILY_LIMIT);
    }

    private void givenExistingDailyTotal(BigDecimal total) {
        when(accountTransfersReadPlatformService.getTotalTransactionAmount(FROM_ACCOUNT_ID, SAVINGS, TRANSFER_DATE)).thenReturn(total);
    }

    private void assertForwarded() {
        CommandProcessingResult expected = mock(CommandProcessingResult.class);
        when(accountTransfersApiResource.create(request)).thenReturn(expected);
        assertSame(expected, resource.create("tpt", request));
    }
}
