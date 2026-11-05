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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.service.CurrencyReadPlatformService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.organisation.office.mapper.OfficeDataMapper;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class OfficeReadPlatformServiceImplTest {

    private static final String PASSWORD_PROBE = "(select ascii(substring(password,1,1)) from m_appuser limit 1)";
    private static final String BLIND_PAYLOAD = "(case when o.id > " + PASSWORD_PROBE + " then o.id else o.parent_id end)";

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private CurrencyReadPlatformService currencyReadPlatformService;
    @Mock
    private OfficeRepository officeRepository;
    @Mock
    private OfficeDataMapper officeDataMapper;

    private OfficeReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OfficeReadPlatformServiceImpl(jdbcTemplate, sqlGenerator, context, currencyReadPlatformService, officeRepository,
                officeDataMapper);
        final Office office = mock(Office.class);
        when(office.getHierarchy()).thenReturn(".");
        final AppUser user = mock(AppUser.class);
        when(user.getOffice()).thenReturn(office);
        when(context.authenticatedUser()).thenReturn(user);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            id, o.id
            o.id, o.id
            name, o.name
            O.NAME, o.name
            externalId, o.external_id
            openingDate, o.opening_date
            hierarchy, o.hierarchy
            parentId, o.parent_id
            parent.id, o.parent_id
            parentName, parent.name
            """)
    void allowListedOrderByIsMappedToFixedColumn(final String orderBy, final String expectedColumn) {
        assertTrue(retrieveAndCaptureSql(orderBy, null).endsWith("order by " + expectedColumn));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            asc, ASC
            DESC, DESC
            ' Desc ', DESC
            """)
    void sortOrderIsNormalizedToAscOrDesc(final String sortOrder, final String expected) {
        assertTrue(retrieveAndCaptureSql("name", sortOrder).endsWith("order by o.name " + expected));
    }

    @Test
    void defaultsToHierarchyOrderWhenOrderByMissing() {
        assertTrue(retrieveAndCaptureSql(null, null).endsWith("order by o.hierarchy"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select 1 from m_appuser)", BLIND_PAYLOAD, "o.id, (select 1)", "unknown_column", "o.id desc" })
    void rejectsOrderByOutsideAllowList(final String orderBy) {
        final PlatformApiDataValidationException exception = assertRejected(SearchParameters.builder().orderBy(orderBy).build());

        assertEquals("orderBy", exception.getErrors().get(0).getParameterName());
    }

    @ParameterizedTest
    @ValueSource(strings = { ",(select 1)", "asc, (select 1)", "ascending", "desc;" })
    void rejectsSortOrderOtherThanAscOrDesc(final String sortOrder) {
        final SearchParameters searchParameters = SearchParameters.builder().orderBy("o.id").sortOrder(sortOrder).build();

        final PlatformApiDataValidationException exception = assertRejected(searchParameters);

        assertEquals("sortOrder", exception.getErrors().get(0).getParameterName());
    }

    private PlatformApiDataValidationException assertRejected(final SearchParameters searchParameters) {
        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> service.retrieveAllOffices(false, searchParameters));
        verifyNoInteractions(jdbcTemplate);
        return exception;
    }

    @SuppressWarnings("unchecked")
    private String retrieveAndCaptureSql(final String orderBy, final String sortOrder) {
        service.retrieveAllOffices(false, SearchParameters.builder().orderBy(orderBy).sortOrder(sortOrder).build());

        final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        return sqlCaptor.getValue().trim();
    }
}
