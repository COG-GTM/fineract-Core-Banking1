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
package org.apache.fineract.portfolio.shareaccounts.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
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
class ShareAccountDividendReadPlatformServiceImplTest {

    private static final Long PAYOUT_ID = 1L;

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PaginationHelper paginationHelper;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;

    private ShareAccountDividendReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(sqlGenerator.calcFoundRows()).thenReturn("");
        lenient().when(paginationHelper.fetchPage(any(), anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(new Page<>(List.of(), 0));
        service = new ShareAccountDividendReadPlatformServiceImpl(jdbcTemplate, paginationHelper, sqlGenerator);
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select sleep((5)))", "(select 1 from pg_sleep(5))", "sadd.id, (select password from m_appuser)",
            "case when 1 then sadd.id end", "unknownColumn", "sadd.amount desc", "" + "sadd.id;" })
    void rejectsOrderByOutsideAllowlist(String orderBy) {
        SearchParameters searchParameters = SearchParameters.builder().orderBy(orderBy).build();

        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> service.retriveAll(PAYOUT_ID, searchParameters));

        assertEquals("orderBy", ex.getErrors().get(0).getParameterName());
        verifyNoInteractions(paginationHelper);
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select sleep((5)))", "asc, (select 1)", "descending", "ASC;" })
    void rejectsSortOrderOtherThanAscOrDesc(String sortOrder) {
        SearchParameters searchParameters = SearchParameters.builder().orderBy("amount").sortOrder(sortOrder).build();

        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> service.retriveAll(PAYOUT_ID, searchParameters));

        assertEquals("sortOrder", ex.getErrors().get(0).getParameterName());
        verifyNoInteractions(paginationHelper);
    }

    @Test
    void mapsAllowedOrderByToFixedColumn() {
        SearchParameters searchParameters = SearchParameters.builder().orderBy("clientName").sortOrder("desc").build();

        service.retriveAll(PAYOUT_ID, searchParameters);

        String sql = captureSql();
        assertTrue(sql.endsWith(" order by mc.display_name DESC"), sql);
    }

    @Test
    void acceptsQualifiedColumnNames() {
        SearchParameters searchParameters = SearchParameters.builder().orderBy("sa.account_no").sortOrder("ASC").build();

        service.retriveAll(PAYOUT_ID, searchParameters);

        assertTrue(captureSql().endsWith(" order by sa.account_no ASC"));
    }

    @Test
    void omitsOrderByWhenNotRequested() {
        service.retriveAll(PAYOUT_ID, SearchParameters.builder().build());

        assertTrue(!captureSql().contains("order by"));
    }

    private String captureSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(eq(jdbcTemplate), sql.capture(), any(Object[].class), any(RowMapper.class));
        return sql.getValue();
    }
}
