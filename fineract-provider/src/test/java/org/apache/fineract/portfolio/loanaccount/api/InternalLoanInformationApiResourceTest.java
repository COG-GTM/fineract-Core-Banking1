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
package org.apache.fineract.portfolio.loanaccount.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanproduct.mapper.AdvancedPaymentDataMapper;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalLoanInformationApiResourceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;
    @Mock
    private LoanTransactionRepository loanTransactionRepository;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private AdvancedPaymentDataMapper advancedPaymentDataMapper;

    @InjectMocks
    private InternalLoanInformationApiResource resource;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void getLoansByStatusRequiresReadLoanPermission() {
        denyReadLoan();

        assertThrows(NoAuthorizationException.class, () -> resource.getLoansByStatus(null, 300));
        verifyNoInteractions(loanRepositoryWrapper);
    }

    @Test
    void getLoanAuditFieldsRequiresReadLoanPermission() {
        denyReadLoan();

        assertThrows(NoAuthorizationException.class, () -> resource.getLoanAuditFields(null, 1L));
        verifyNoInteractions(loanRepositoryWrapper);
    }

    @Test
    void getLoanTransactionAuditFieldsRequiresReadLoanPermission() {
        denyReadLoan();

        assertThrows(NoAuthorizationException.class, () -> resource.getLoanTransactionAuditFields(null, 1L, 2L));
        verifyNoInteractions(loanTransactionRepository);
    }

    @Test
    void getAdvancedPaymentAllocationRulesRequiresReadLoanPermission() {
        denyReadLoan();

        assertThrows(NoAuthorizationException.class, () -> resource.getAdvancedPaymentAllocationRulesOfLoan(null, 1L));
        verifyNoInteractions(loanRepositoryWrapper, advancedPaymentDataMapper);
    }

    @Test
    void getLoansByStatusReturnsIdsWhenPermitted() {
        when(loanRepositoryWrapper.findLoanIdsByStatusId(300)).thenReturn(List.of(1L, 2L));

        assertEquals(List.of(1L, 2L), resource.getLoansByStatus(null, 300));
        verify(user).validateHasReadPermission("LOAN");
    }

    private void denyReadLoan() {
        doThrow(new NoAuthorizationException("User has no authority to: READ_LOAN")).when(user).validateHasReadPermission("LOAN");
    }
}
