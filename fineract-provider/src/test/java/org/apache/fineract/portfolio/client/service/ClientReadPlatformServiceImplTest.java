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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.portfolio.client.data.ClientData;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.apache.fineract.portfolio.client.mapper.ClientMapper;
import org.apache.fineract.portfolio.collateralmanagement.domain.ClientCollateralManagementRepositoryWrapper;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private CodeValueReadPlatformService codeValueReadPlatformService;
    @Mock
    private PaginationHelper paginationHelper;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private ColumnValidator columnValidator;
    @Mock
    private ClientCollateralManagementRepositoryWrapper collateralRepoWrapper;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private ClientMapper clientMapper;

    @InjectMocks
    private ClientReadPlatformServiceImpl clientReadPlatformService;

    @Test
    void testRetrieveOne_Success() {
        // Arrange
        Long clientId = 1L;
        String mockHierarchy = "Root/";
        Client mockClientEntity = mock(Client.class);

        // FIX: Using .lookup() instead of .builder() to match Fineract's ClientData patterns
        ClientData mockClientData = ClientData.lookup(clientId, "Test Client", 1L, "Test Office");

        // Stubbing dependencies
        when(context.officeHierarchy()).thenReturn(mockHierarchy);
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(clientId, mockHierarchy + "%")).thenReturn(mockClientEntity);
        when(clientMapper.map(mockClientEntity)).thenReturn(mockClientData);
        when(collateralRepoWrapper.getCollateralsPerClient(clientId)).thenReturn(Collections.emptyList());

        // Mock the groups query to return an empty list
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyLong())).thenReturn(Collections.emptyList());

        // Act
        ClientData result = clientReadPlatformService.retrieveOne(clientId);

        // Assert
        assertNotNull(result);
        assertEquals(clientId, result.getId());
    }

    @Test
    void testRetrieveOne_ClientNotFound_ThrowsException() {
        // Arrange
        Long clientId = 99L;
        when(context.officeHierarchy()).thenReturn("Root/");

        // Simulate database "Not Found" error
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(anyLong(), anyString()))
                .thenThrow(new EmptyResultDataAccessException(1));

        // Act & Assert
        assertThrows(ClientNotFoundException.class, () -> {
            clientReadPlatformService.retrieveOne(clientId);
        });
    }

    @Test
    void testRetrieveAll_ThrowsExceptionWhenStatusIsInvalid() {
        // Arrange
        // We create a SearchParameters object with an "INVALID" status
        org.apache.fineract.infrastructure.core.service.SearchParameters searchParameters = org.apache.fineract.infrastructure.core.service.SearchParameters
                .builder().status("INVALID").build();

        // Act & Assert
        // The service should catch the "INVALID" status and throw a validation exception
        assertThrows(org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException.class, () -> {
            clientReadPlatformService.retrieveAll(searchParameters);
        });
    }

    private void stubListQueryContext() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(1L);
        when(context.officeHierarchy()).thenReturn(".");
        when(context.authenticatedUser()).thenReturn(user);
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select sleep(5))",
            "if(strcmp(substring((select username from m_appuser limit 1),1,1),'a'),c.id,c.display_name)",
            "case when 1 then c.id else c.display_name end", "c.id, (select 1)", "display_name desc", "sleep(strcmp(c.id,1)*5)" })
    void testRetrieveAll_RejectsOrderByOutsideAllowlist(String orderBy) {
        stubListQueryContext();
        SearchParameters searchParameters = SearchParameters.builder().orderBy(orderBy).build();

        assertThrows(PlatformApiDataValidationException.class, () -> clientReadPlatformService.retrieveAll(searchParameters));
        verifyNoInteractions(paginationHelper);
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select sleep(5))", "ASC, (select 1)", "desc limit 1", "ASCENDING" })
    void testRetrieveAll_RejectsSortOrderOtherThanAscOrDesc(String sortOrder) {
        stubListQueryContext();
        SearchParameters searchParameters = SearchParameters.builder().orderBy("displayName").sortOrder(sortOrder).build();

        assertThrows(PlatformApiDataValidationException.class, () -> clientReadPlatformService.retrieveAll(searchParameters));
        verifyNoInteractions(paginationHelper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRetrieveAll_MapsAllowedOrderByToFixedColumn() {
        stubListQueryContext();
        SearchParameters searchParameters = SearchParameters.builder().orderBy("displayName").sortOrder("desc").build();

        clientReadPlatformService.retrieveAll(searchParameters);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(any(JdbcTemplate.class), sql.capture(), any(Object[].class), any(RowMapper.class));
        assertTrue(sql.getValue().endsWith(" order by c.display_name DESC"), sql.getValue());
    }

    @Test
    void testResolveClientOrderByColumn_AcceptsApiAndColumnNames() {
        assertEquals("c.id", ClientReadPlatformServiceImpl.resolveClientOrderByColumn("id"));
        assertEquals("c.account_no", ClientReadPlatformServiceImpl.resolveClientOrderByColumn("accountNo"));
        assertEquals("o.name", ClientReadPlatformServiceImpl.resolveClientOrderByColumn("officeName"));
        assertEquals("c.display_name", ClientReadPlatformServiceImpl.resolveClientOrderByColumn("c.display_name"));
        assertEquals("c.submittedon_date", ClientReadPlatformServiceImpl.resolveClientOrderByColumn("submittedondate"));
        assertEquals("ASC", ClientReadPlatformServiceImpl.resolveSortOrder("asc"));
    }
}
