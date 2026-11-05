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
package org.apache.fineract.portfolio.repaymentwithpostdatedchecks.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallmentRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.data.PostDatedChecksData;
import org.apache.fineract.portfolio.repaymentwithpostdatedchecks.domain.PostDatedChecksRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepaymentWithPostDatedChecksReadPlatformServiceImplTest {

    private static final Long LOAN_ID = 41L;

    @Mock
    private PostDatedChecksRepository postDatedChecksRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private LoanRepaymentScheduleInstallmentRepository loanRepaymentScheduleInstallmentRepository;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private Loan loan;
    @Mock
    private Client client;

    private RepaymentWithPostDatedChecksReadPlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        underTest = new RepaymentWithPostDatedChecksReadPlatformServiceImpl(postDatedChecksRepository, loanRepository,
                loanRepaymentScheduleInstallmentRepository, context);
        when(loanRepository.findById(LOAN_ID)).thenReturn(Optional.of(loan));
        when(loan.getClient()).thenReturn(client);
        when(loan.getPostDatedChecks()).thenReturn(List.of());
    }

    @Test
    void getPostDatedChecksAllowsLoanInUserOfficeHierarchy() {
        when(context.officeHierarchy()).thenReturn(".1.");
        final Office loanOffice = office(".1.2.");
        when(loan.getOffice()).thenReturn(loanOffice);

        List<PostDatedChecksData> result = underTest.getPostDatedChecks(LOAN_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void getPostDatedChecksAllowsLoanTransferredIntoUserOffice() {
        when(context.officeHierarchy()).thenReturn(".1.3.");
        final Office loanOffice = office(".1.2.");
        final Office transferToOffice = office(".1.3.");
        when(loan.getOffice()).thenReturn(loanOffice);
        when(client.getTransferToOffice()).thenReturn(transferToOffice);

        assertTrue(underTest.getPostDatedChecks(LOAN_ID).isEmpty());
    }

    @Test
    void getPostDatedChecksRejectsLoanOutsideUserOfficeHierarchy() {
        when(context.officeHierarchy()).thenReturn(".1.3.");
        final Office loanOffice = office(".1.2.");
        when(loan.getOffice()).thenReturn(loanOffice);

        LoanNotFoundException e = assertThrows(LoanNotFoundException.class, () -> underTest.getPostDatedChecks(LOAN_ID));
        assertEquals("error.msg.loan.id.invalid", e.getGlobalisationMessageCode());
    }

    @Test
    void getPostDatedCheckByInstallmentIdRejectsLoanOutsideUserOfficeHierarchy() {
        when(context.officeHierarchy()).thenReturn(".1.3.");
        final Office loanOffice = office(".1.2.");
        when(loan.getOffice()).thenReturn(loanOffice);

        assertThrows(LoanNotFoundException.class, () -> underTest.getPostDatedCheckByInstallmentId(1, LOAN_ID));
    }

    private static Office office(final String hierarchy) {
        final Office office = mock(Office.class);
        when(office.getHierarchy()).thenReturn(hierarchy);
        return office;
    }
}
