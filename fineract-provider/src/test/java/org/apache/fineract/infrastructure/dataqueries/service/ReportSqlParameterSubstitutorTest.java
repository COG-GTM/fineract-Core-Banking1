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
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportSqlParameterSubstitutorTest {

    private static final String LOAN_OFFICER_SQL = "select lo.id, lo.display_name as `Name` from m_office o"
            + " join m_staff lo on lo.office_id = o.id and lo.is_loan_officer = true"
            + " where o.hierarchy like concat('${currentUserHierarchy}', '%') and o.id = ${officeId}";

    private static String substitute(final String sql, final Map<String, String> requestParams) {
        return substitute(sql, requestParams, true);
    }

    private static String substitute(final String sql, final Map<String, String> requestParams, final boolean mysql) {
        final Map<String, String> trustedParams = Map.of("${currentUserHierarchy}", ".1.", "${currentUserId}", "7");
        return ReportSqlParameterSubstitutor.substitute(sql, requestParams, trustedParams, mysql);
    }

    @Test
    void substitutesNumericAndQuotedValues() {
        assertEquals("select 1 from t where o.id = -1 and d >= '2023-01-01' and c = 'USD' and dd = DATE '2024-02-29' and n in (1, 2)",
                substitute(
                        "select 1 from t where o.id = ${officeId} and d >= '${startDate}' and c = '${currencyId}'"
                                + " and dd = DATE ${endDate} and n in (${ids})",
                        Map.of("${officeId}", "-1", "${startDate}", "2023-01-01", "${currencyId}", "USD", "${endDate}", "'2024-02-29'",
                                "${ids}", "1, 2")));
    }

    @Test
    void substitutesQuotedLiteralList() {
        assertEquals("select 1 from stretchy_report where report_name in('Client Listing','Active Loans - Details')",
                substitute("select 1 from stretchy_report where report_name in(${reportListing})",
                        Map.of("${reportListing}", "'Client Listing','Active Loans - Details'")));
    }

    @Test
    void escapesQuotesInsideLiterals() {
        assertEquals("select 1 from t where name = 'O''Brien'' or ''1''=''1' and code = \"a\"\" or 1=1 -- \"",
                substitute("select 1 from t where name = '${name}' and code = \"${code}\"",
                        Map.of("${name}", "O'Brien' or '1'='1", "${code}", "a\" or 1=1 -- ")));
    }

    @Test
    void trustedValuesCannotBeOverriddenByRequest() {
        final Map<String, String> requestParams = Map.of("${currentUserHierarchy}", ".", "${currentUserId}", "1", "${officeId}", "1");
        assertEquals("select 1 from m_office o where o.hierarchy like concat('.1.', '%') and o.id = 1 and u = 7",
                substitute("select 1 from m_office o where o.hierarchy like concat('${currentUserHierarchy}', '%') and o.id = ${officeId}"
                        + " and u = ${currentUserId}", requestParams));
    }

    @Test
    void valuesAreNotRescannedForPlaceholders() {
        assertEquals("select 1 from t where a = '${currentUserHierarchy}' and b = 2",
                substitute("select 1 from t where a = '${a}' and b = ${b}", Map.of("${a}", "${currentUserHierarchy}", "${b}", "2")));
    }

    @Test
    void ignoresPlaceholderLikeTextInComments() {
        final String sql = "select 1 /* it's ${officeId} */ from t -- don't ${officeId}\nwhere o.id = ${officeId}";
        assertEquals("select 1 /* it's ${officeId} */ from t -- don't ${officeId}\nwhere o.id = 3",
                substitute(sql, Map.of("${officeId}", "3")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0) union all (select username, password from m_appuser", "0 or exists(select 1 from m_appuser)", "1 or 1=1",
            "0/**/union/**/select/**/1", "1;drop table m_office", "1 -- ", "abc", "'a' union select 'b'", "'a\\', 'b'", "1e1" })
    void rejectsSqlInUnquotedContext(final String payload) {
        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> substitute(LOAN_OFFICER_SQL, Map.of("${officeId}", payload)));
        assertEquals("R_officeId", exception.getErrors().get(0).getParameterName());
    }

    @ParameterizedTest
    @ValueSource(strings = { "2023-01-01\\' OR 1=1 -- ", "a\\", "a\u0000b" })
    void rejectsBackslashAndNulInsideLiterals(final String payload) {
        assertThrows(PlatformApiDataValidationException.class,
                () -> substitute("select 1 from t where d >= '${startDate}'", Map.of("${startDate}", payload)));
    }

    @Test
    void rejectsNonNumericValueInsideIdentifier() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> substitute("select `${column}` from t", Map.of("${column}", "password")));
    }

    @Test
    void ignoresPlaceholdersInHashComments() {
        assertEquals("select 1 # ${officeId}\nwhere o.id = 1",
                substitute("select 1 # ${officeId}\nwhere o.id = ${officeId}", Map.of("${officeId}", "1")));
    }

    @Test
    void hashCommentApostropheCannotHideUnquotedPlaceholder() {
        final String sql = "select 1 from m_office o # don't\nwhere o.id = ${officeId}";
        assertThrows(PlatformApiDataValidationException.class, () -> substitute(sql, Map.of("${officeId}", "0 or 2>1")));
        assertEquals("select 1 from m_office o # don't\nwhere o.id = 5", substitute(sql, Map.of("${officeId}", "5")));
    }

    @Test
    void afterBackslashOnlyContextFreeValuesAreAccepted() {
        final String sql = "select 'C:\\' as p, x from t where c = '${currencyId}' and o.id = ${officeId}";
        assertThrows(PlatformApiDataValidationException.class, () -> substitute(sql, Map.of("${officeId}", "0 or 2>1")));
        assertThrows(PlatformApiDataValidationException.class, () -> substitute(sql, Map.of("${currencyId}", "USD")));
        assertEquals("select 'C:\\' as p, x from t where c = '2024-01-31' and o.id = 3",
                substitute(sql, Map.of("${currencyId}", "2024-01-31", "${officeId}", "3")));
    }

    @Test
    void negativeValueCannotFormLineComment() {
        assertEquals("select 5 - -1 from t", substitute("select 5 -${n} from t", Map.of("${n}", "-1")));
        assertEquals("select 5 - -1 from t", substitute("select 5 - ${n} from t", Map.of("${n}", "-1")));
    }

    @Test
    void hashIsAnOperatorInPostgresql() {
        assertEquals("select 5 # 3", substitute("select 5 # ${mask}", Map.of("${mask}", "3"), false));
        assertThrows(PlatformApiDataValidationException.class,
                () -> substitute("select 1 # 1 where o.id = ${officeId}", Map.of("${officeId}", "0 or 2>1"), false));
    }

    @Test
    void placeholderAfterLiteralBackslashIsSubstituted() {
        assertEquals("select '\\3'", substitute("select '\\${part}'", Map.of("${part}", "3"), false));
        assertEquals("select '\\3'", substitute("select '\\${part}'", Map.of("${part}", "3"), true));
    }

    @Test
    void postgresqlStandardStringBackslashDoesNotHideUnquotedPlaceholder() {
        final String sql = "select 'C:\\' as p from m_office o where o.id = ${officeId}";
        assertThrows(PlatformApiDataValidationException.class, () -> substitute(sql, Map.of("${officeId}", "0 or 2>1"), false));
        assertEquals("select 'C:\\' as p from m_office o where o.id = 5", substitute(sql, Map.of("${officeId}", "5"), false));
    }

    @Test
    void postgresqlEscapeStringBackslashDoesNotHideUnquotedPlaceholder() {
        final String sql = "select E'\\'' as p from m_office o where o.id = ${officeId}";
        assertThrows(PlatformApiDataValidationException.class, () -> substitute(sql, Map.of("${officeId}", "0 or 2>1"), false));
        assertEquals("select E'\\'' as p from m_office o where o.id = 5", substitute(sql, Map.of("${officeId}", "5"), false));
    }

    @Test
    void negativeValueInsideLiteralIsNotSpaced() {
        assertEquals("select 'prefix--1'", substitute("select 'prefix-${value}'", Map.of("${value}", "-1")));
    }
}
