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
package org.apache.fineract.infrastructure.reportmailingjob.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
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
class ReportMailingJobRunHistoryReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ColumnValidator columnValidator;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private PaginationHelper paginationHelper;

    private ReportMailingJobRunHistoryReadPlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        lenient().when(sqlGenerator.calcFoundRows()).thenReturn("");
        lenient().when(sqlGenerator.limit(any(Integer.class), any(Integer.class))).thenReturn("LIMIT 10 OFFSET 0");
        underTest = new ReportMailingJobRunHistoryReadPlatformServiceImpl(jdbcTemplate, columnValidator, sqlGenerator, paginationHelper);
    }

    @ParameterizedTest
    @CsvSource({ "start_datetime, desc, rmjrh.start_datetime DESC", "startDateTime, ASC, rmjrh.start_datetime ASC",
            "END_DATETIME, Desc, rmjrh.end_datetime DESC", "id, asc, rmjrh.id ASC", "status, desc, rmjrh.status DESC",
            "reportMailingJobId, asc, rmjrh.job_id ASC" })
    void allowedOrderByAndSortOrderAreMappedToFixedColumns(String orderBy, String sortOrder, String expectedOrderClause) {
        underTest.retrieveRunHistoryByJobId(1L,
                SearchParameters.builder().limit(10).offset(0).orderBy(orderBy).sortOrder(sortOrder).build());

        String sql = captureExecutedSql();
        assertTrue(sql.endsWith(" order by " + expectedOrderClause + " LIMIT 10 OFFSET 0"), sql);
    }

    @Test
    void orderByWithoutSortOrderIsMapped() {
        underTest.retrieveRunHistoryByJobId(null, SearchParameters.builder().limit(10).offset(0).orderBy("status").build());

        String sql = captureExecutedSql();
        assertTrue(sql.endsWith(" order by rmjrh.status LIMIT 10 OFFSET 0"), sql);
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select pg_sleep(5))", "(select 1 from pg_sleep(5))", "(select sleep(5))",
            "(select cast((select password from m_appuser limit 1) as int))", "rmjrh.id", "id desc, (select 1)", "1" })
    void unsupportedOrderByIsRejectedBeforeQueryExecution(String orderBy) {
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.retrieveRunHistoryByJobId(null, SearchParameters.builder().orderBy(orderBy).build()));

        assertEquals("validation.msg.reportmailingjobrunhistory.orderBy.value.is.not.supported",
                exception.getErrors().get(0).getUserMessageGlobalisationCode());
        verifyNoInteractions(paginationHelper);
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select pg_sleep(5))", "desc, (select 1 from pg_sleep(5))", "sideways", "asc nulls first" })
    void unsupportedSortOrderIsRejectedBeforeQueryExecution(String sortOrder) {
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.retrieveRunHistoryByJobId(null, SearchParameters.builder().orderBy("id").sortOrder(sortOrder).build()));

        assertEquals("validation.msg.reportmailingjobrunhistory.sortOrder.value.is.not.supported",
                exception.getErrors().get(0).getUserMessageGlobalisationCode());
        verifyNoInteractions(paginationHelper);
    }

    @SuppressWarnings("unchecked")
    private String captureExecutedSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(eq(jdbcTemplate), sql.capture(), any(Object[].class), any(RowMapper.class));
        return sql.getValue();
    }
}
