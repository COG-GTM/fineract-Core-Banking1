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
package org.apache.fineract.portfolio.collateralmanagement.domain;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.apache.fineract.portfolio.collateralmanagement.exception.ClientCollateralNotFoundException;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientCollateralManagementRepositoryWrapperTest {

    private static final String HIERARCHY = ".1.%";

    @Mock
    private ClientCollateralManagementRepository clientCollateralManagementRepository;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private LoanProductRepository loanProductRepository;

    private ClientCollateralManagementRepositoryWrapper wrapper;

    @BeforeEach
    void setUp() {
        wrapper = new ClientCollateralManagementRepositoryWrapper(clientCollateralManagementRepository, clientRepositoryWrapper,
                loanProductRepository);
    }

    @Test
    void getCollateralForClientReturnsCollateralOwnedByClient() {
        Client client = clientWithId(1L);
        ClientCollateralManagement collateral = collateralOwnedBy(clientWithId(1L));
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(1L, HIERARCHY)).thenReturn(client);
        when(clientCollateralManagementRepository.findById(57L)).thenReturn(Optional.of(collateral));

        assertSame(collateral, wrapper.getCollateralForClient(1L, 57L, HIERARCHY));
    }

    @Test
    void getCollateralForClientRejectsCollateralOfAnotherClient() {
        Client client = clientWithId(1L);
        ClientCollateralManagement collateral = collateralOwnedBy(clientWithId(2L));
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(1L, HIERARCHY)).thenReturn(client);
        when(clientCollateralManagementRepository.findById(57L)).thenReturn(Optional.of(collateral));

        assertThrows(ClientCollateralNotFoundException.class, () -> wrapper.getCollateralForClient(1L, 57L, HIERARCHY));
    }

    @Test
    void getCollateralForClientRejectsClientOutsideOfficeHierarchy() {
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(2L, HIERARCHY))
                .thenThrow(new ClientNotFoundException("2", "client.id"));

        assertThrows(ClientNotFoundException.class, () -> wrapper.getCollateralForClient(2L, 57L, HIERARCHY));
        verify(clientCollateralManagementRepository, never()).findById(any());
    }

    @Test
    void getCollateralForClientRejectsUnknownCollateral() {
        Client client = clientWithId(1L);
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(1L, HIERARCHY)).thenReturn(client);
        when(clientCollateralManagementRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ClientCollateralNotFoundException.class, () -> wrapper.getCollateralForClient(1L, 99L, HIERARCHY));
    }

    private static Client clientWithId(Long id) {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(id);
        return client;
    }

    private static ClientCollateralManagement collateralOwnedBy(Client owner) {
        ClientCollateralManagement collateral = mock(ClientCollateralManagement.class);
        when(collateral.getClient()).thenReturn(owner);
        return collateral;
    }
}
