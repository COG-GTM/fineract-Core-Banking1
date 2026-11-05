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
package org.apache.fineract.infrastructure.sms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SmsReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private PaginationHelper paginationHelper;

    @InjectMocks
    private SmsReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service.init();
        when(sqlGenerator.calcFoundRows()).thenReturn("");
        when(sqlGenerator.limit(any(Integer.class))).thenReturn("LIMIT 200");
    }

    private String executedSql(String orderBy, String sortOrder) {
        SearchParameters params = SearchParameters.builder().orderBy(orderBy).sortOrder(sortOrder).build();
        service.retrieveSmsByStatus(1L, params, 100, null, null);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(eq(jdbcTemplate), sql.capture(), any(Object[].class), any(RowMapper.class));
        return sql.getValue();
    }

    @Test
    void defaultOrderingWhenNoOrderBy() {
        assertTrue(executedSql(null, null).contains(" order by smo.submittedon_date, smo.id"));
    }

    @Test
    void allowlistedOrderByAndSortOrderAreMappedToColumns() {
        assertTrue(executedSql("smo.id", "desc").contains(" order by smo.id DESC"));
    }

    @Test
    void orderByAcceptsResponseFieldNames() {
        assertTrue(executedSql("submittedOnDate", "ASC").contains(" order by smo.submittedon_date ASC"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select sleep(5))", "(select sleep(5*strcmp(left(password,1),'b')) from m_appuser limit 1)",
            "smo.id, (select 1)", "smo.id desc", "smo.message", "1", "if(1,smo.id,smo.client_id)" })
    void rejectsOrderByOutsideAllowlist(String orderBy) {
        SearchParameters params = SearchParameters.builder().orderBy(orderBy).build();
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> service.retrieveSmsByStatus(1L, params, 100, null, null));
        assertEquals("orderBy", ex.getErrors().get(0).getParameterName());
        verifyNoInteractions(paginationHelper);
    }

    @ParameterizedTest
    @ValueSource(strings = { "desc, (select sleep(5))", "asc;", "descending", "(select 1)" })
    void rejectsSortOrderOtherThanAscOrDesc(String sortOrder) {
        SearchParameters params = SearchParameters.builder().orderBy("smo.id").sortOrder(sortOrder).build();
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> service.retrieveSmsByStatus(1L, params, 100, null, null));
        assertEquals("sortOrder", ex.getErrors().get(0).getParameterName());
        verifyNoInteractions(paginationHelper);
    }
}
