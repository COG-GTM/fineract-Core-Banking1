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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.collateralmanagement.data.ClientCollateralManagementData;
import org.apache.fineract.portfolio.collateralmanagement.domain.ClientCollateralManagement;
import org.apache.fineract.portfolio.collateralmanagement.domain.ClientCollateralManagementRepositoryWrapper;
import org.apache.fineract.portfolio.collateralmanagement.domain.CollateralManagementDomain;
import org.apache.fineract.portfolio.collateralmanagement.exception.ClientCollateralNotFoundException;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientCollateralManagementReadPlatformServiceImplTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private ClientCollateralManagementRepositoryWrapper clientCollateralManagementRepositoryWrapper;
    @Mock
    private LoanTransactionRepository loanTransactionRepository;

    @InjectMocks
    private ClientCollateralManagementReadPlatformServiceImpl service;

    @Test
    void getClientCollateralManagementDataScopesLookupToClientAndOfficeHierarchy() {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(1L);
        CollateralManagementDomain product = mock(CollateralManagementDomain.class);
        when(product.getName()).thenReturn("Gold");
        when(product.getBasePrice()).thenReturn(BigDecimal.TEN);
        when(product.getPctToBase()).thenReturn(BigDecimal.valueOf(50));
        ClientCollateralManagement collateral = mock(ClientCollateralManagement.class);
        when(collateral.getId()).thenReturn(57L);
        when(collateral.getClient()).thenReturn(client);
        when(collateral.getCollaterals()).thenReturn(product);
        when(collateral.getQuantity()).thenReturn(BigDecimal.ONE);
        when(collateral.getLoanCollateralManagementSet()).thenReturn(Collections.emptySet());
        when(context.officeHierarchy()).thenReturn(".1.");
        when(clientCollateralManagementRepositoryWrapper.getCollateralForClient(1L, 57L, ".1.%")).thenReturn(collateral);

        ClientCollateralManagementData data = service.getClientCollateralManagementData(1L, 57L);

        assertEquals(57L, data.getId());
        assertEquals(1L, data.getClientId());
        assertEquals("Gold", data.getName());
    }

    @Test
    void getClientCollateralManagementDataRejectsCollateralOfAnotherClient() {
        when(context.officeHierarchy()).thenReturn(".1.");
        when(clientCollateralManagementRepositoryWrapper.getCollateralForClient(1L, 57L, ".1.%"))
                .thenThrow(new ClientCollateralNotFoundException(57L));

        assertThrows(ClientCollateralNotFoundException.class, () -> service.getClientCollateralManagementData(1L, 57L));
    }
}
