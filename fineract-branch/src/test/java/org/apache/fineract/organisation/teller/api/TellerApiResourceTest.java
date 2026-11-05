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
package org.apache.fineract.organisation.teller.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.teller.data.CashierData;
import org.apache.fineract.organisation.teller.data.CashierTransactionsWithSummaryData;
import org.apache.fineract.organisation.teller.data.TellerData;
import org.apache.fineract.organisation.teller.exception.CashierNotFoundException;
import org.apache.fineract.organisation.teller.service.TellerManagementReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TellerApiResourceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private TellerManagementReadPlatformService readPlatformService;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandWritePlatformService;
    @Mock
    private DefaultToApiJsonSerializer<String> apiJsonSerializer;

    private TellerApiResource resource;

    @BeforeEach
    void setUp() {
        resource = new TellerApiResource(context, readPlatformService, commandWritePlatformService, apiJsonSerializer);
        when(context.authenticatedUser()).thenReturn(user);
    }

    private void denyRead() {
        doThrow(new NoAuthorizationException("User has no authority to READ tellers")).when(user).validateHasReadPermission("TELLER");
    }

    @Test
    void listTellersRequiresReadTellerPermission() {
        denyRead();
        assertThrows(NoAuthorizationException.class, () -> resource.getTellerData(null));
        verifyNoInteractions(readPlatformService);
    }

    @Test
    void findTellerRequiresReadTellerPermission() {
        denyRead();
        assertThrows(NoAuthorizationException.class, () -> resource.findTeller(1L));
        verifyNoInteractions(readPlatformService);
    }

    @Test
    void cashierReadsRequireReadTellerPermission() {
        denyRead();
        assertThrows(NoAuthorizationException.class, () -> resource.getCashierData(1L, null, null));
        assertThrows(NoAuthorizationException.class, () -> resource.findCashierData(1L, 7L));
        assertThrows(NoAuthorizationException.class, () -> resource.getCashierTemplate(1L));
        assertThrows(NoAuthorizationException.class, () -> resource.getTransactionsForCashier(1L, 7L, "USD", null, null, null, null));
        assertThrows(NoAuthorizationException.class,
                () -> resource.getTransactionsWithSummaryForCashier(3L, 7L, "USD", null, null, null, null));
        assertThrows(NoAuthorizationException.class, () -> resource.getCashierTxnTemplate(1L, 7L));
        assertThrows(NoAuthorizationException.class, () -> resource.getTransactionData(1L, null));
        assertThrows(NoAuthorizationException.class, () -> resource.findTransactionData(1L, 1L));
        assertThrows(NoAuthorizationException.class, () -> resource.getJournalData(1L, null, null));
        verifyNoInteractions(readPlatformService);
    }

    @Test
    void cashierOfAnotherTellerIsNotFound() {
        when(readPlatformService.findCashier(7L)).thenReturn(new CashierData().setId(7L).setTellerId(99L));

        assertThrows(CashierNotFoundException.class, () -> resource.findCashierData(3L, 7L));
        assertThrows(CashierNotFoundException.class,
                () -> resource.getTransactionsWithSummaryForCashier(3L, 7L, "USD", null, null, null, null));
        assertThrows(CashierNotFoundException.class, () -> resource.getTransactionsForCashier(3L, 7L, "USD", null, null, null, null));
        assertThrows(CashierNotFoundException.class, () -> resource.getCashierTxnTemplate(3L, 7L));
    }

    @Test
    void permittedUserReadsOwnTellerCashier() {
        final CashierData cashier = new CashierData().setId(7L).setTellerId(3L);
        final CashierTransactionsWithSummaryData summary = CashierTransactionsWithSummaryData.instance(null, null, null, null, null, null,
                3L, null, 7L, null);
        when(readPlatformService.findCashier(7L)).thenReturn(cashier);
        when(readPlatformService.retrieveCashierTransactionsWithSummary(anyLong(), any(Boolean.class), any(), any(), any(),
                any(SearchParameters.class))).thenReturn(summary);
        when(readPlatformService.getTellers(null)).thenReturn(List.of(new TellerData()));

        assertSame(cashier, resource.findCashierData(3L, 7L));
        assertSame(summary, resource.getTransactionsWithSummaryForCashier(3L, 7L, "USD", null, null, null, null));
        resource.getTellerData(null);
        verify(user, times(3)).validateHasReadPermission("TELLER");
    }
}
