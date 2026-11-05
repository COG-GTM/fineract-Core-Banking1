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
package org.apache.fineract.organisation.office.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.office.domain.OfficeTransaction;
import org.apache.fineract.organisation.office.domain.OfficeTransactionRepository;
import org.apache.fineract.organisation.office.exception.OfficeTransactionNotFoundException;
import org.apache.fineract.organisation.office.serialization.OfficeCommandFromApiJsonDeserializer;
import org.apache.fineract.organisation.office.serialization.OfficeTransactionCommandFromApiJsonDeserializer;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficeWritePlatformServiceJpaRepositoryImplTest {

    private static final Long TRANSACTION_ID = 99L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private OfficeCommandFromApiJsonDeserializer officeDeserializer;
    @Mock
    private OfficeTransactionCommandFromApiJsonDeserializer officeTransactionDeserializer;
    @Mock
    private OfficeRepositoryWrapper officeRepositoryWrapper;
    @Mock
    private OfficeTransactionRepository officeTransactionRepository;
    @Mock
    private ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository;
    @Mock
    private JsonCommand command;
    @Mock
    private AppUser currentUser;

    private MockedStatic<MoneyHelper> moneyHelperStatic;
    private OfficeWritePlatformServiceJpaRepositoryImpl underTest;

    private final Office headOffice = office(1L, ".");
    private final Office branch = office(2L, ".2.");
    private final Office subBranch = office(4L, ".2.4.");
    private final Office otherBranch = office(3L, ".3.");
    private final Office otherBranchWithSimilarPrefix = office(21L, ".21.");

    @BeforeEach
    void setUp() {
        underTest = new OfficeWritePlatformServiceJpaRepositoryImpl(context, officeDeserializer, officeTransactionDeserializer,
                officeRepositoryWrapper, officeTransactionRepository, applicationCurrencyRepository);
        moneyHelperStatic = Mockito.mockStatic(MoneyHelper.class);
        when(context.authenticatedUser()).thenReturn(currentUser);
        lenient().when(currentUser.getOffice()).thenReturn(branch);
    }

    @AfterEach
    void tearDown() {
        moneyHelperStatic.close();
    }

    @Test
    void createRejectsTransactionBetweenOfficesOutsideUserHierarchy() {
        givenTransferRequest(headOffice, otherBranch);

        assertThrows(NoAuthorizationException.class, () -> underTest.officeTransaction(command));

        verify(officeTransactionRepository, never()).save(any());
    }

    @Test
    void createRejectsOfficeWhoseHierarchyOnlySharesATextPrefix() {
        givenTransferRequest(null, otherBranchWithSimilarPrefix);

        assertThrows(NoAuthorizationException.class, () -> underTest.officeTransaction(command));

        verify(officeTransactionRepository, never()).save(any());
    }

    @Test
    void createAllowsTransferFromUserOfficeToParentOffice() {
        givenTransferRequest(branch, headOffice);
        givenCurrency();

        final CommandProcessingResult result = underTest.officeTransaction(command);

        verify(officeTransactionRepository).save(any(OfficeTransaction.class));
        assertEquals(headOffice.getId(), result.getOfficeId());
    }

    @Test
    void createAllowsTransferIntoChildOfficeFromOutside() {
        givenTransferRequest(otherBranch, subBranch);
        givenCurrency();

        underTest.officeTransaction(command);

        verify(officeTransactionRepository).save(any(OfficeTransaction.class));
    }

    @Test
    void deleteRejectsTransactionBetweenOfficesOutsideUserHierarchy() {
        final OfficeTransaction transaction = transaction(headOffice, otherBranch);
        when(officeTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));

        assertThrows(NoAuthorizationException.class, () -> underTest.deleteOfficeTransaction(TRANSACTION_ID, command));

        verify(officeTransactionRepository, never()).delete(any(OfficeTransaction.class));
        verify(officeTransactionRepository, never()).deleteById(any());
    }

    @Test
    void deleteAllowsTransactionInvolvingUserHierarchy() {
        final OfficeTransaction transaction = transaction(subBranch, otherBranch);
        when(officeTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));

        final CommandProcessingResult result = underTest.deleteOfficeTransaction(TRANSACTION_ID, command);

        verify(officeTransactionRepository).delete(transaction);
        assertEquals(TRANSACTION_ID, result.getResourceId());
    }

    @Test
    void deleteFailsForUnknownTransaction() {
        when(officeTransactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

        assertThrows(OfficeTransactionNotFoundException.class, () -> underTest.deleteOfficeTransaction(TRANSACTION_ID, command));

        verify(officeTransactionRepository, never()).delete(any(OfficeTransaction.class));
    }

    private void givenTransferRequest(final Office from, final Office to) {
        givenOfficeParameter("fromOfficeId", from);
        givenOfficeParameter("toOfficeId", to);
    }

    private void givenOfficeParameter(final String parameterName, final Office office) {
        if (office == null) {
            when(command.longValueOfParameterNamed(parameterName)).thenReturn(null);
            return;
        }
        final Long officeId = office.getId();
        when(command.longValueOfParameterNamed(parameterName)).thenReturn(officeId);
        when(officeRepositoryWrapper.findOneWithNotFoundDetection(officeId)).thenReturn(office);
    }

    private void givenCurrency() {
        moneyHelperStatic.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        final ApplicationCurrency currency = mock(ApplicationCurrency.class);
        when(currency.getCode()).thenReturn("USD");
        when(currency.getDecimalPlaces()).thenReturn(2);
        when(command.stringValueOfParameterNamed("currencyCode")).thenReturn("USD");
        when(command.bigDecimalValueOfParameterNamed("transactionAmount")).thenReturn(BigDecimal.TEN);
        when(applicationCurrencyRepository.findOneWithNotFoundDetection("USD")).thenReturn(currency);
    }

    private static OfficeTransaction transaction(final Office from, final Office to) {
        final OfficeTransaction transaction = mock(OfficeTransaction.class);
        when(transaction.getFrom()).thenReturn(from);
        when(transaction.getTo()).thenReturn(to);
        return transaction;
    }

    private static Office office(final Long id, final String hierarchy) {
        final Office office = mock(Office.class);
        lenient().when(office.getId()).thenReturn(id);
        lenient().when(office.getHierarchy()).thenReturn(hierarchy);
        return office;
    }
}
