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
package org.apache.fineract.infrastructure.jobs.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.api.IdTypeResolver;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class SchedulerJobRunnerReadServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private ScheduledJobDetailRepository jobDetailRepository;
    @Mock
    private PaginationHelper paginationHelper;

    private SchedulerJobRunnerReadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchedulerJobRunnerReadServiceImpl(jdbcTemplate, sqlGenerator, jobDetailRepository, paginationHelper);
        when(jobDetailRepository.existsById(1L)).thenReturn(true);
        lenient().when(sqlGenerator.calcFoundRows()).thenReturn("");
        lenient().when(sqlGenerator.escape(anyString())).thenAnswer(i -> "\"" + i.getArgument(0) + "\"");
        lenient().when(sqlGenerator.limit(anyInt(), anyInt())).thenAnswer(i -> "LIMIT " + i.getArgument(0) + " OFFSET " + i.getArgument(1));
    }

    private String executedSql(String orderBy, String sortOrder) {
        service.retrieveJobHistory(IdTypeResolver.IdType.ID, "1",
                SearchParameters.builder().orderBy(orderBy).sortOrder(sortOrder).limit(10).offset(0).build());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(eq(jdbcTemplate), sql.capture(), any(Object[].class), any(RowMapper.class));
        return sql.getValue();
    }

    @Test
    void allowlistedOrderByAndSortOrderAreMappedToFixedColumns() {
        assertTrue(executedSql("start_time", "desc").endsWith("where job.id = ? order by runHistory.start_time DESC LIMIT 10 OFFSET 0"));
    }

    @Test
    void responseFieldAliasesAreAccepted() {
        assertTrue(executedSql(" runStartTime ", "ASC").contains(" order by runHistory.start_time ASC "));
    }

    @Test
    void statusColumnIsEscaped() {
        assertTrue(executedSql("STATUS", null).contains(" order by runHistory.\"status\" LIMIT"));
    }

    @Test
    void noOrderByLeavesQueryUnordered() {
        assertFalse(executedSql(null, "desc").contains("order by"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select pg_sleep(5))", "(select sleep(5))", "start_time, (select 1)", "runHistory.start_time", "error_log",
            "1", "case when 1 then start_time end" })
    void injectedOrderByIsRejectedBeforeQueryRuns(String orderBy) {
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> service.retrieveJobHistory(IdTypeResolver.IdType.ID, "1", SearchParameters.builder().orderBy(orderBy).build()));
        assertEquals("orderBy", ex.getErrors().get(0).getParameterName());
        verify(paginationHelper, never()).fetchPage(any(), anyString(), any(Object[].class), any(RowMapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "desc, (select pg_sleep(5))", "(select sleep(5))", "descending", "asc;" })
    void injectedSortOrderIsRejectedBeforeQueryRuns(String sortOrder) {
        SearchParameters params = SearchParameters.builder().orderBy("version").sortOrder(sortOrder).build();
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> service.retrieveJobHistory(IdTypeResolver.IdType.ID, "1", params));
        assertEquals("sortOrder", ex.getErrors().get(0).getParameterName());
        verify(paginationHelper, never()).fetchPage(any(), anyString(), any(Object[].class), any(RowMapper.class));
    }
}
