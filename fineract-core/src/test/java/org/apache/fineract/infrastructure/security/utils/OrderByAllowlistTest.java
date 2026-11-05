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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrderByAllowlistTest {

    private final OrderByAllowlist allowlist = OrderByAllowlist.of(Map.of("id", "sa.id", "accountNo", "sa.account_no"));

    @Test
    void buildsClauseFromAllowedValues() {
        assertEquals(" order by sa.account_no DESC", allowlist.toOrderByClause("accountNo", "desc"));
        assertEquals(" order by sa.account_no ASC", allowlist.toOrderByClause(" ACCOUNTNO ", "Asc"));
        assertEquals(" order by sa.id", allowlist.toOrderByClause("id", null));
        assertEquals(" order by sa.account_no DESC", allowlist.toOrderByClause("SA.ACCOUNT_NO", "DESC"));
        assertEquals("", allowlist.toOrderByClause(null, null));
        assertEquals("", allowlist.toOrderByClause("", "asc"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "(case when sbu.password like 'a%' then sa.id else -sa.id end)",
            "(select 1 from m_appuser u where u.id < 2)", "sa.id, sbu.password", "sbu.password", "sa.password", "1", "id desc", "id;" })
    void rejectsOrderByOutsideAllowlist(String orderBy) {
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> allowlist.toOrderByClause(orderBy, "asc"));
        assertEquals(OrderByAllowlist.ORDER_BY_PARAMETER, ex.getErrors().get(0).getParameterName());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ascending", "desc, sbu.password", "asc nulls first", "(select 1)", "desc;" })
    void rejectsSortOrderOtherThanAscOrDesc(String sortOrder) {
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> allowlist.toOrderByClause("id", sortOrder));
        assertEquals(OrderByAllowlist.SORT_ORDER_PARAMETER, ex.getErrors().get(0).getParameterName());
        assertThrows(PlatformApiDataValidationException.class, () -> allowlist.toOrderByClause(null, sortOrder));
    }
}
