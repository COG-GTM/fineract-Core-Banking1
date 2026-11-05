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
package org.apache.fineract.portfolio.collateralmanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.collateralmanagement.data.LoanCollateralResponseData;
import org.apache.fineract.portfolio.collateralmanagement.domain.ClientCollateralManagement;
import org.apache.fineract.portfolio.collateralmanagement.domain.CollateralManagementDomain;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCollateralManagement;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCollateralManagementRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanCollateralManagementReadPlatformServiceImplTest {

    private static final Long COLLATERAL_ID = 57L;
    private static final String LOAN_OFFICE_HIERARCHY = ".1.2.";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private LoanCollateralManagementRepository loanCollateralManagementRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private LoanCollateralManagement loanCollateralManagement;

    private LoanCollateralManagementReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LoanCollateralManagementReadPlatformServiceImpl(context, loanCollateralManagementRepository, loanRepository);

        Office office = mock(Office.class);
        when(office.getHierarchy()).thenReturn(LOAN_OFFICE_HIERARCHY);
        Loan loan = mock(Loan.class);
        when(loan.getOffice()).thenReturn(office);
        when(loanCollateralManagement.getLoanData()).thenReturn(loan);
        when(loanCollateralManagementRepository.findById(COLLATERAL_ID)).thenReturn(Optional.of(loanCollateralManagement));
    }

    @Test
    void getLoanCollateralResponseDataRejectsUserOutsideLoanOfficeHierarchy() {
        doThrow(new NoAuthorizationException("The user doesn't have enough permissions to access the resource.")).when(context)
                .validateAccessRights(LOAN_OFFICE_HIERARCHY);

        assertThrows(NoAuthorizationException.class, () -> service.getLoanCollateralResponseData(COLLATERAL_ID));
    }

    @Test
    void getLoanCollateralResponseDataReturnsDataForUserInLoanOfficeHierarchy() {
        CollateralManagementDomain product = mock(CollateralManagementDomain.class);
        when(product.getBasePrice()).thenReturn(BigDecimal.valueOf(100));
        when(product.getPctToBase()).thenReturn(BigDecimal.valueOf(80));
        ClientCollateralManagement clientCollateral = mock(ClientCollateralManagement.class);
        when(clientCollateral.getCollaterals()).thenReturn(product);
        when(clientCollateral.getId()).thenReturn(3L);
        when(loanCollateralManagement.getClientCollateralManagement()).thenReturn(clientCollateral);
        when(loanCollateralManagement.getQuantity()).thenReturn(BigDecimal.valueOf(2));
        when(loanCollateralManagement.getId()).thenReturn(COLLATERAL_ID);

        LoanCollateralResponseData data = service.getLoanCollateralResponseData(COLLATERAL_ID);

        verify(context).validateAccessRights(LOAN_OFFICE_HIERARCHY);
        assertEquals(COLLATERAL_ID, data.getCollateralId());
        assertEquals(0, BigDecimal.valueOf(200).compareTo(data.getTotal()));
        assertEquals(0, BigDecimal.valueOf(160).compareTo(data.getTotalCollateral()));
        assertEquals(3L, data.getClientCollateralId());
    }
}
