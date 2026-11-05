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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlSortingUtilsTest {

    private static final Map<String, String> COLUMNS = SqlSortingUtils
            .sortableColumns(Map.of("id", "l.id", "accountNo", "l.account_no", "l.account_no", "l.account_no"));

    @Test
    void resolvesAllowlistedOrderByCaseInsensitively() {
        assertEquals("l.account_no", SqlSortingUtils.resolveOrderBy("loan", "accountNo", COLUMNS));
        assertEquals("l.account_no", SqlSortingUtils.resolveOrderBy("loan", " ACCOUNTNO ", COLUMNS));
        assertEquals("l.account_no", SqlSortingUtils.resolveOrderBy("loan", "l.account_no", COLUMNS));
        assertEquals("l.id", SqlSortingUtils.resolveOrderBy("loan", "id", COLUMNS));
    }

    @Test
    void blankOrderByAndSortOrderResolveToNull() {
        assertNull(SqlSortingUtils.resolveOrderBy("loan", null, COLUMNS));
        assertNull(SqlSortingUtils.resolveOrderBy("loan", " ", COLUMNS));
        assertNull(SqlSortingUtils.resolveSortOrder("loan", null));
        assertNull(SqlSortingUtils.resolveSortOrder("loan", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select pg_sleep('5'))", "(select sleep(pi()))", "l.id--", "l.id; with x as (select 1) select 1",
            "case when (select 1) then l.id else l.account_no end", "l.account_no desc", "l.id,l.account_no", "unknown" })
    void rejectsOrderByOutsideAllowlist(String orderBy) {
        assertThrows(PlatformApiDataValidationException.class, () -> SqlSortingUtils.resolveOrderBy("loan", orderBy, COLUMNS));
    }

    @Test
    void normalizesSortOrder() {
        assertEquals("ASC", SqlSortingUtils.resolveSortOrder("loan", "asc"));
        assertEquals("DESC", SqlSortingUtils.resolveSortOrder("loan", " Desc "));
    }

    @ParameterizedTest
    @ValueSource(strings = { "DESC, (select pg_sleep('5'))", "desc--", "descending", "; do $$ begin end $$" })
    void rejectsInvalidSortOrder(String sortOrder) {
        assertThrows(PlatformApiDataValidationException.class, () -> SqlSortingUtils.resolveSortOrder("loan", sortOrder));
    }
}
