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
package org.apache.fineract.portfolio.client.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.ClientTransactionData;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientTransactionRepository;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class ClientTransactionReadPlatformServiceImplTest {

    private static final String USER_HIERARCHY = ".1.2.";
    private static final Long CLIENT_ID = 7L;

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private PaginationHelper paginationHelper;
    @Mock
    private ClientTransactionRepository clientTransactionRepository;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;

    private ClientTransactionReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClientTransactionReadPlatformServiceImpl(jdbcTemplate, sqlGenerator, paginationHelper, clientTransactionRepository,
                context, clientRepositoryWrapper);
        when(context.officeHierarchy()).thenReturn(USER_HIERARCHY);
    }

    private void clientOutsideHierarchy() {
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(CLIENT_ID, USER_HIERARCHY + "%"))
                .thenThrow(new ClientNotFoundException(CLIENT_ID.toString(), "client.id"));
    }

    @Test
    void retrieveAllTransactionsPagedRejectsClientOutsideUserHierarchy() {
        clientOutsideHierarchy();

        assertThrows(ClientNotFoundException.class,
                () -> service.retrieveAllTransactions(CLIENT_ID, SearchParameters.builder().limit(10).offset(0).build()));
        verifyNoInteractions(jdbcTemplate, paginationHelper);
    }

    @Test
    void retrieveAllTransactionsByChargeRejectsClientOutsideUserHierarchy() {
        clientOutsideHierarchy();

        assertThrows(ClientNotFoundException.class, () -> service.retrieveAllTransactions(CLIENT_ID, 3L));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void retrieveTransactionRejectsClientOutsideUserHierarchy() {
        clientOutsideHierarchy();

        assertThrows(ClientNotFoundException.class, () -> service.retrieveTransaction(CLIENT_ID, 11L));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieveAllTransactionsPagedReturnsTransactionsForClientInUserHierarchy() {
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(CLIENT_ID, USER_HIERARCHY + "%")).thenReturn(mock(Client.class));
        final Page<ClientTransactionData> page = new Page<>(List.of(), 0);
        when(paginationHelper.fetchPage(eq(jdbcTemplate), anyString(), any(Object[].class), any(RowMapper.class))).thenReturn(page);

        assertSame(page, service.retrieveAllTransactions(CLIENT_ID, SearchParameters.builder().build()));
        verify(clientRepositoryWrapper).getClientByClientIdAndHierarchy(CLIENT_ID, USER_HIERARCHY + "%");
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieveAllTransactionsByChargeReturnsTransactionsForClientInUserHierarchy() {
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(CLIENT_ID, USER_HIERARCHY + "%")).thenReturn(mock(Client.class));
        final List<ClientTransactionData> rows = List.of();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(CLIENT_ID), eq(3L))).thenReturn(rows);

        final Collection<ClientTransactionData> result = service.retrieveAllTransactions(CLIENT_ID, 3L);

        assertSame(rows, result);
    }
}
