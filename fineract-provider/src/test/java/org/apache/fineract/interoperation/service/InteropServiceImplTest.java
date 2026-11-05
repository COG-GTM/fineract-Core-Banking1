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
package org.apache.fineract.interoperation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.interoperation.data.InteropIdentifierAccountResponseData;
import org.apache.fineract.interoperation.domain.InteropIdentifier;
import org.apache.fineract.interoperation.domain.InteropIdentifierRepository;
import org.apache.fineract.interoperation.domain.InteropIdentifierType;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class InteropServiceImplTest {

    private static final String ACCOUNT_ID = "acc-ext-id";
    private static final String OTHER_BRANCH_HIERARCHY = ".2.";

    @Mock
    private PlatformSecurityContext securityContext;
    @Mock
    private SavingsAccountRepository savingsAccountRepository;
    @Mock
    private InteropIdentifierRepository identifierRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private InteropServiceImpl service;

    @Test
    void getAccountDetailsRejectsAccountOutsideUserOfficeHierarchy() {
        stubAccountInOtherBranch();

        assertThrows(NoAuthorizationException.class, () -> service.getAccountDetails(ACCOUNT_ID));
        verify(securityContext).validateAccessRights(OTHER_BRANCH_HIERARCHY);
    }

    @Test
    void getAccountTransactionsRejectsAccountOutsideUserOfficeHierarchy() {
        stubAccountInOtherBranch();

        assertThrows(NoAuthorizationException.class, () -> service.getAccountTransactions(ACCOUNT_ID, true, false, null, null));
        verify(securityContext).validateAccessRights(OTHER_BRANCH_HIERARCHY);
    }

    @Test
    void getAccountIdentifiersRejectsAccountOutsideUserOfficeHierarchy() {
        stubAccountInOtherBranch();

        assertThrows(NoAuthorizationException.class, () -> service.getAccountIdentifiers(ACCOUNT_ID));
        verify(securityContext).validateAccessRights(OTHER_BRANCH_HIERARCHY);
    }

    @Test
    void getKycRejectsAccountOutsideUserOfficeHierarchyBeforeQueryingClientData() {
        stubAccountInOtherBranch();

        assertThrows(NoAuthorizationException.class, () -> service.getKyc(ACCOUNT_ID));
        verify(securityContext).validateAccessRights(OTHER_BRANCH_HIERARCHY);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void getAccountByIdentifierRejectsAccountOutsideUserOfficeHierarchy() {
        SavingsAccount account = accountInOffice(OTHER_BRANCH_HIERARCHY);
        InteropIdentifier identifier = mock(InteropIdentifier.class);
        when(identifier.getAccount()).thenReturn(account);
        when(identifierRepository.findOneByTypeAndValueAndSubType(InteropIdentifierType.MSISDN, "27710203999", null))
                .thenReturn(identifier);
        doThrow(new NoAuthorizationException("denied")).when(securityContext).validateAccessRights(OTHER_BRANCH_HIERARCHY);

        assertThrows(NoAuthorizationException.class,
                () -> service.getAccountByIdentifier(InteropIdentifierType.MSISDN, "27710203999", null));
    }

    @Test
    void getAccountByIdentifierReturnsAccountInsideUserOfficeHierarchy() {
        SavingsAccount account = accountInOffice(".1.");
        when(account.getExternalId()).thenReturn(new ExternalId(ACCOUNT_ID));
        InteropIdentifier identifier = mock(InteropIdentifier.class);
        when(identifier.getAccount()).thenReturn(account);
        when(identifierRepository.findOneByTypeAndValueAndSubType(InteropIdentifierType.MSISDN, "27710203999", null))
                .thenReturn(identifier);

        InteropIdentifierAccountResponseData result = service.getAccountByIdentifier(InteropIdentifierType.MSISDN, "27710203999",
                null);

        assertEquals(ACCOUNT_ID, result.getAccountId());
        verify(securityContext).validateAccessRights(".1.");
    }

    private void stubAccountInOtherBranch() {
        SavingsAccount account = accountInOffice(OTHER_BRANCH_HIERARCHY);
        when(savingsAccountRepository.findByExternalId(any(ExternalId.class))).thenReturn(account);
        doThrow(new NoAuthorizationException("denied")).when(securityContext).validateAccessRights(OTHER_BRANCH_HIERARCHY);
    }

    private static SavingsAccount accountInOffice(String hierarchy) {
        Office office = mock(Office.class);
        when(office.getHierarchy()).thenReturn(hierarchy);
        SavingsAccount account = mock(SavingsAccount.class);
        when(account.office()).thenReturn(office);
        return account;
    }
}
