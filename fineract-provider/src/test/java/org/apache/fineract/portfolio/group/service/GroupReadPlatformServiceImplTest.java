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
package org.apache.fineract.portfolio.group.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import org.apache.fineract.infrastructure.core.data.PaginationParameters;
import org.apache.fineract.infrastructure.core.data.PaginationParametersDataValidator;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SuppressFBWarnings(value = "RV_EXCEPTION_NOT_THROWN", justification = "False positive")
@ExtendWith(MockitoExtension.class)
class GroupReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private PaginationHelper paginationHelper;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private ColumnValidator columnValidator;
    @Spy
    private PaginationParametersDataValidator paginationParametersDataValidator = new PaginationParametersDataValidator();

    @InjectMocks
    private GroupReadPlatformServiceImpl groupReadPlatformService;

    @BeforeEach
    void setUp() {
        final Office office = mock(Office.class);
        lenient().when(office.getHierarchy()).thenReturn(".");
        final AppUser user = mock(AppUser.class);
        lenient().when(user.getOffice()).thenReturn(office);
        lenient().when(context.authenticatedUser()).thenReturn(user);
    }

    @ParameterizedTest
    @ValueSource(strings = { "g.id*0", "if(strcmp(1,2),g.id,g.display_name)", "(select 1)", "name, g.id", "o.name" })
    void retrieveAllRejectsUnsupportedOrderBy(final String orderBy) {
        final PaginationParameters parameters = PaginationParameters.builder().orderBy(orderBy).build();

        assertThrows(PlatformApiDataValidationException.class,
                () -> groupReadPlatformService.retrieveAll(groupSearch(orderBy, null), parameters));
        verifyNoInteractions(jdbcTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = { "desc, (select 1)", "asc limit 1", "case when 1 then 1 end" })
    void retrieveAllRejectsUnsupportedSortOrder(final String sortOrder) {
        final PaginationParameters parameters = PaginationParameters.builder().orderBy("name").sortOrder(sortOrder).build();

        assertThrows(PlatformApiDataValidationException.class,
                () -> groupReadPlatformService.retrieveAll(groupSearch("name", sortOrder), parameters));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void retrieveAllOrdersByMappedColumn() {
        final PaginationParameters parameters = PaginationParameters.builder().orderBy("name").sortOrder("desc").build();

        final String sql = executeRetrieveAll(groupSearch("name", "desc"), parameters);

        assertTrue(sql.endsWith(" order by g.display_name DESC"), sql);
    }

    @Test
    void retrieveAllDefaultsToAscendingOrder() {
        final PaginationParameters parameters = PaginationParameters.builder().orderBy("officeName").limit(0).build();

        final String sql = executeRetrieveAll(groupSearch("officeName", null), parameters);

        assertTrue(sql.endsWith(" order by o.name ASC"), sql);
    }

    @Test
    void retrieveAllWithoutParametersHasNoOrderBy() {
        final String sql = executeRetrieveAll(null, null);

        assertFalse(sql.contains(" order by "), sql);
    }

    @Test
    void retrievePagedAllRejectsUnsupportedOrderBy() {
        final String orderBy = "if(strcmp(1,2),g.id,g.display_name)";
        final PaginationParameters parameters = PaginationParameters.builder().paged(true).orderBy(orderBy).build();

        assertThrows(PlatformApiDataValidationException.class,
                () -> groupReadPlatformService.retrievePagedAll(groupSearch(orderBy, null), parameters));
        verify(paginationHelper, never()).fetchPage(any(), anyString(), any(), any());
    }

    private String executeRetrieveAll(final SearchParameters searchParameters, final PaginationParameters parameters) {
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sql.capture(), any(RowMapper.class), any(Object[].class))).thenReturn(Collections.emptyList());

        groupReadPlatformService.retrieveAll(searchParameters, parameters);

        return sql.getValue();
    }

    private static SearchParameters groupSearch(final String orderBy, final String sortOrder) {
        return SearchParameters.builder().orderBy(orderBy).sortOrder(sortOrder).isSelfUser(false).build();
    }
}
