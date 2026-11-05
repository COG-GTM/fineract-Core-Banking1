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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlOrderByAllowlistTest {

    private final SqlOrderByAllowlist allowlist = new SqlOrderByAllowlist(
            Map.of("name", "atsi.name", "fromClientName", "fromclient.display_name"));

    private static SearchParameters params(String orderBy, String sortOrder) {
        return SearchParameters.builder().orderBy(orderBy).sortOrder(sortOrder).build();
    }

    @Test
    void returnsEmptyClauseWhenNoOrderByRequested() {
        assertEquals("", allowlist.orderByClause(null));
        assertEquals("", allowlist.orderByClause(params(null, null)));
        assertEquals("", allowlist.orderByClause(params(" ", "desc")));
    }

    @Test
    void mapsAliasAndQualifiedColumnToQualifiedColumn() {
        assertEquals(" order by atsi.name DESC", allowlist.orderByClause(params("name", "DESC")));
        assertEquals(" order by atsi.name ASC", allowlist.orderByClause(params("NAME", "asc")));
        assertEquals(" order by fromclient.display_name", allowlist.orderByClause(params("fromclientname", null)));
        assertEquals(" order by fromclient.display_name", allowlist.orderByClause(params("fromclient.display_name", "")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select 1 from pg_sleep(5))", "(select sleep(5))", "name,(select 1)", "atsi.name desc", "1",
            "case when 1=1 then name end", "unknownColumn", "m_appuser.password" })
    void rejectsOrderByOutsideAllowlist(String orderBy) {
        PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> allowlist.orderByClause(params(orderBy, "ASC")));
        assertEquals("orderBy", e.getErrors().get(0).getParameterName());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ASC,(select 1 from pg_sleep(5))", "desc nulls first", "(select 1)", "ascending" })
    void rejectsSortOrderOtherThanAscOrDesc(String sortOrder) {
        PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> allowlist.orderByClause(params("name", sortOrder)));
        assertEquals("sortOrder", e.getErrors().get(0).getParameterName());
    }
}
