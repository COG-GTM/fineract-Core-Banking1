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
        final Map<String, String> trustedParams = Map.of("${currentUserHierarchy}", ".1.", "${currentUserId}", "7");
        return ReportSqlParameterSubstitutor.substitute(sql, requestParams, trustedParams);
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
}
