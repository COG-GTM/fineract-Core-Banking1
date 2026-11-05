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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.utils.SqlSortingUtils;
import org.junit.jupiter.api.Test;

class LoanReadPlatformServiceImplSortingTest {

    private static final Map<String, String> COLUMNS = LoanReadPlatformServiceImpl.LOAN_SORTABLE_COLUMNS;

    @Test
    void sortableColumnsArePlainQualifiedColumns() {
        COLUMNS.values().forEach(column -> assertTrue(column.matches("[a-z]+\\.[a-z_]+"), "unexpected sortable column: " + column));
    }

    @Test
    void documentedAndLegacyOrderByValuesStillResolve() {
        assertEquals("l.account_no", SqlSortingUtils.resolveOrderBy("loan", "accountNo", COLUMNS));
        assertEquals("l.id", SqlSortingUtils.resolveOrderBy("loan", "id", COLUMNS));
        assertEquals("l.id", SqlSortingUtils.resolveOrderBy("loan", "l.id", COLUMNS));
    }

    @Test
    void injectedOrderByIsRejected() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> SqlSortingUtils.resolveOrderBy("loan", "(select count(*) from pg_sleep('5'))", COLUMNS));
    }
}
