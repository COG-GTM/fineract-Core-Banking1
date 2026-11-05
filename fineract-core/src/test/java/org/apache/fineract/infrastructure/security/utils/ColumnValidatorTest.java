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
package org.apache.fineract.infrastructure.security.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class ColumnValidatorTest {

    private final SqlValidator sqlValidator = Mockito.mock(SqlValidator.class);
    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final ColumnValidator underTest = new ColumnValidator(sqlValidator, jdbcTemplate);

    @ParameterizedTest
    @ValueSource(strings = { "id", "displayName", "c.id", "journalEntry.id", "account_no", "_x1", "  id  " })
    void validateOrderByAcceptsSingleIdentifier(String orderBy) {
        assertDoesNotThrow(() -> underTest.validateOrderBy(orderBy));
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select sleep((5)))",
            "(select case ascii(substring((select password from m_appuser limit 1),1,1)) when 36 then sleep((5)) else 1 end)",
            "c.id desc, (select 1)", "case when 1 then 1 else 2 end", "id desc", "c.id,c.display_name", "pg_sleep(5)", "1", "a.b.c",
            "id;drop table m_client", "id -- x", "`id`", "c.id/**/" })
    void validateOrderByRejectsExpressions(String orderBy) {
        assertThrows(PlatformApiDataValidationException.class, () -> underTest.validateOrderBy(orderBy));
    }

    @ParameterizedTest
    @ValueSource(strings = { "asc", "desc", "ASC", "DESC", "Desc", " asc " })
    void validateSortOrderAcceptsDirection(String sortOrder) {
        assertDoesNotThrow(() -> underTest.validateSortOrder(sortOrder));
    }

    @ParameterizedTest
    @ValueSource(strings = { "dsc", "asc, (select 1)", "desc limit 1", ", (select sleep((5)))", "asc nulls first", "(select 1)" })
    void validateSortOrderRejectsAnythingElse(String sortOrder) {
        assertThrows(PlatformApiDataValidationException.class, () -> underTest.validateSortOrder(sortOrder));
    }

    @Test
    void blankValuesAreIgnored() {
        assertDoesNotThrow(() -> underTest.validateOrderBy(null, null));
        assertDoesNotThrow(() -> underTest.validateOrderBy(" ", ""));
        assertDoesNotThrow(() -> underTest.validateOrderByList(null));
    }

    @Test
    void validateOrderByWithSortOrderChecksBoth() {
        assertDoesNotThrow(() -> underTest.validateOrderBy("c.id", "desc"));
        assertThrows(PlatformApiDataValidationException.class, () -> underTest.validateOrderBy("c.id", "desc, (select 1)"));
        assertThrows(PlatformApiDataValidationException.class, () -> underTest.validateOrderBy("(select 1)", "asc"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "id", "id desc", "`Date of Birth` desc", "created_at ASC, id", "\"Date of Birth\" asc" })
    void validateOrderByListAcceptsColumnsWithDirection(String order) {
        assertDoesNotThrow(() -> underTest.validateOrderByList(order));
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select sleep((5)))", "id, (select 1)", "id desc limit 1", "`a`` b`", "id,", "case when 1 then 1 else 2 end",
            "`x` desc; drop table m_client" })
    void validateOrderByListRejectsExpressions(String order) {
        assertThrows(PlatformApiDataValidationException.class, () -> underTest.validateOrderByList(order));
    }
}
