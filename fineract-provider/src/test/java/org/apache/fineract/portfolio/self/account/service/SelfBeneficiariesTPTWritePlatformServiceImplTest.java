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
package org.apache.fineract.portfolio.self.account.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.self.account.api.SelfBeneficiariesTPTApiConstants;
import org.apache.fineract.portfolio.self.account.data.SelfBeneficiariesTPTDataValidator;
import org.apache.fineract.portfolio.self.account.domain.SelfBeneficiariesTPT;
import org.apache.fineract.portfolio.self.account.domain.SelfBeneficiariesTPTRepository;
import org.apache.fineract.portfolio.self.account.exception.BeneficiaryAddAttemptsExceededException;
import org.apache.fineract.portfolio.self.account.exception.InvalidAccountInformationException;
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
class SelfBeneficiariesTPTWritePlatformServiceImplTest {

    private static final String OFFICE = "Head Office";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private SelfBeneficiariesTPTRepository repository;
    @Mock
    private SelfBeneficiariesTPTDataValidator validator;
    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;
    @Mock
    private SavingsAccountRepositoryWrapper savingsRepositoryWrapper;
    @Mock
    private AppUser user;
    @Mock
    private JsonCommand command;

    private SelfBeneficiariesTPTWritePlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SelfBeneficiariesTPTWritePlatformServiceImpl(context, repository, validator, loanRepositoryWrapper,
                savingsRepositoryWrapper, new SelfBeneficiariesTPTAddThrottle());
        when(context.authenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(7L);
        when(command.json()).thenReturn("{}");
        when(savingsRepositoryWrapper.findNonClosedAccountByAccountNumber(anyString())).thenAnswer(invocation -> {
            String accountNumber = invocation.getArgument(0);
            if (!accountNumber.startsWith("0")) {
                return null;
            }
            SavingsAccount savings = mock(SavingsAccount.class, RETURNS_DEEP_STUBS);
            when(savings.getId()).thenReturn(Long.valueOf(accountNumber));
            when(savings.getClient().getId()).thenReturn(100L + Long.parseLong(accountNumber));
            when(savings.getClient().getOffice().getId()).thenReturn(1L);
            when(savings.getClient().getOffice().getName()).thenReturn(OFFICE);
            return savings;
        });
    }

    @Test
    void failedLookupsLockTheFeatureForTheUser() {
        for (int i = 0; i < SelfBeneficiariesTPTAddThrottle.DEFAULT_MAX_FAILED_ATTEMPTS; i++) {
            givenRequest("9" + i, "guess" + i);
            assertThrows(InvalidAccountInformationException.class, () -> service.add(command));
        }

        givenRequest("000000001", "valid");
        assertThrows(BeneficiaryAddAttemptsExceededException.class, () -> service.add(command));
        verify(savingsRepositoryWrapper, times(SelfBeneficiariesTPTAddThrottle.DEFAULT_MAX_FAILED_ATTEMPTS))
                .findNonClosedAccountByAccountNumber(anyString());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void successfulRegistrationsAreCappedToo() {
        for (int i = 1; i <= SelfBeneficiariesTPTAddThrottle.DEFAULT_MAX_ATTEMPTS; i++) {
            givenRequest(String.format("%09d", i), "b" + i);
            service.add(command);
        }

        givenRequest("000000099", "one-too-many");
        assertThrows(BeneficiaryAddAttemptsExceededException.class, () -> service.add(command));
        verify(repository, times(SelfBeneficiariesTPTAddThrottle.DEFAULT_MAX_ATTEMPTS)).saveAndFlush(any(SelfBeneficiariesTPT.class));
        verify(savingsRepositoryWrapper, never()).findNonClosedAccountByAccountNumber("000000099");
    }

    @Test
    void validRegistrationIsStoredForTheCaller() {
        givenRequest("000000005", "friend");
        service.add(command);
        verify(repository).saveAndFlush(any(SelfBeneficiariesTPT.class));
    }

    private void givenRequest(String accountNumber, String name) {
        HashMap<String, Object> params = new HashMap<>();
        params.put(SelfBeneficiariesTPTApiConstants.NAME_PARAM_NAME, name);
        params.put(SelfBeneficiariesTPTApiConstants.ACCOUNT_TYPE_PARAM_NAME, PortfolioAccountType.SAVINGS.getValue());
        params.put(SelfBeneficiariesTPTApiConstants.ACCOUNT_NUMBER_PARAM_NAME, accountNumber);
        params.put(SelfBeneficiariesTPTApiConstants.OFFICE_NAME_PARAM_NAME, OFFICE);
        params.put(SelfBeneficiariesTPTApiConstants.TRANSFER_LIMIT_PARAM_NAME, 0L);
        when(validator.validateForCreate(anyString())).thenReturn(params);
    }
}
