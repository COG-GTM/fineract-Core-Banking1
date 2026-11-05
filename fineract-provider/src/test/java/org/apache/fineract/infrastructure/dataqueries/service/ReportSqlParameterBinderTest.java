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
package org.apache.fineract.infrastructure.dataqueries.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.apache.fineract.infrastructure.security.exception.SqlValidationException;
import org.junit.jupiter.api.Test;

class ReportSqlParameterBinderTest {

    private static final Map<String, String> TRUSTED = Map.of("${currentUserHierarchy}", ".1.");

    private static String bind(String sql, Map<String, String> userParams) {
        return ReportSqlParameterBinder.bind(sql, userParams, TRUSTED);
    }

    @Test
    void substitutesNumericAndQuotedValues() {
        String sql = "select * from m_loan where office_id = ${officeId} and disbursedon_date <= '${endDate}'";
        assertEquals("select * from m_loan where office_id = -1 and disbursedon_date <= '2024-01-31'",
                bind(sql, Map.of("${officeId}", "-1", "${endDate}", "2024-01-31")));
    }

    @Test
    void escapesQuotesInsideStringLiterals() {
        assertEquals("where name = 'O''Brien' or name = \"say \"\"hi\"\"\"",
                bind("where name = '${a}' or name = \"${b}\"", Map.of("${a}", "O'Brien", "${b}", "say \"hi\"")));
    }

    @Test
    void cannotBreakOutOfStringLiteral() {
        String sql = "where hierarchy like concat('${h}', '%')";
        String payload = "x') union all select id,password from m_appuser where '1'<>concat('";
        assertEquals("where hierarchy like concat('x'') union all select id,password from m_appuser where ''1''<>concat(''', '%')",
                bind(sql, Map.of("${h}", payload)));
    }

    @Test
    void rejectsBackslashInsideStringLiteral() {
        assertThrows(SqlValidationException.class, () -> bind("where a = '${a}'", Map.of("${a}", "x\\' or 1<>2 or '")));
    }

    @Test
    void rejectsSqlInUnquotedContext() {
        assertThrows(SqlValidationException.class,
                () -> bind("where office_id = ${officeId}", Map.of("${officeId}", "1 union all select id,password from m_appuser")));
        assertThrows(SqlValidationException.class, () -> bind("where office_id = ${officeId}", Map.of("${officeId}", "1/**/or/**/1")));
        String listPayload = "'a') union all select password from m_appuser -- ";
        assertThrows(SqlValidationException.class, () -> bind("where r in(${reportListing})", Map.of("${reportListing}", listPayload)));
    }

    @Test
    void acceptsListsOfQuotedStringsInUnquotedContext() {
        assertEquals("where r in('Client Listing', 'Bob''s Report',3)",
                bind("where r in(${reportListing})", Map.of("${reportListing}", "'Client Listing', 'Bob''s Report',3")));
    }

    @Test
    void trustedValuesWinOverUserValues() {
        assertEquals("like '.1.%'", bind("like '${currentUserHierarchy}%'", Map.of("${currentUserHierarchy}", "x")));
    }

    @Test
    void valuesAreNotRescanned() {
        assertEquals("a = '${currentUserHierarchy}' and b = '.1.'",
                bind("a = '${a}' and b = '${currentUserHierarchy}'", Map.of("${a}", "${currentUserHierarchy}")));
    }

    @Test
    void tracksQuoteStateAcrossEscapedQuotesAndComments() {
        assertEquals("select 'it''s ${x}' as a, ${x} as b -- ${x}\n, `c` /* ${x} */",
                bind("select 'it''s ${x}' as a, ${x} as b -- ${x}\n, `c` /* ${x} */", Map.of()));
        assertThrows(SqlValidationException.class, () -> bind("select 'it''s' as a, ${x} as b", Map.of("${x}", "1 or 1")));
        assertEquals("select 'it''s' as a, 7 as b -- ${x}", bind("select 'it''s' as a, ${x} as b -- ${x}", Map.of("${x}", "7")));
    }

    @Test
    void leavesUnknownPlaceholdersUntouched() {
        assertEquals("where a = ${missing}", bind("where a = ${missing}", Map.of()));
    }
}
