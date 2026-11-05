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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportSqlParameterSubstitutorTest {

    private static final String FULL_PARAMETER_LIST = "select sp.parameter_name from stretchy_parameter sp where exists (select 'f' "
            + "from stretchy_report sr where sr.report_name in(${reportListing}) and srp.parameter_id = sp.id) order by sp.id";

    @Test
    void substitutesNumericAndQuotedPlaceholders() {
        final String sql = "select * from m_loan l where l.office_id = ${officeId}"
                + " and l.disbursedon_date between '${startDate}' and '${endDate}'";
        assertEquals("select * from m_loan l where l.office_id = 1 and l.disbursedon_date between '2024-01-01' and '2024-12-31'",
                substitute(sql, Map.of("${officeId}", "1", "${startDate}", "2024-01-01", "${endDate}", "2024-12-31")));
    }

    @Test
    void acceptsPlainKeysAsUsedByCampaigns() {
        assertEquals("select 1 where id = 5", substitute("select 1 where id = ${loanId}", Map.of("loanId", "5")));
    }

    @Test
    void acceptsQuotedListForUnquotedPlaceholder() {
        assertEquals(FULL_PARAMETER_LIST.replace("${reportListing}", "'Client Listing','Loan Listing'"),
                substitute(FULL_PARAMETER_LIST, Map.of("${reportListing}", "'Client Listing','Loan Listing'")));
        assertEquals(FULL_PARAMETER_LIST.replace("${reportListing}", "'O''Brien'"),
                substitute(FULL_PARAMETER_LIST, Map.of("${reportListing}", "'O''Brien'")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "'x') union all select 1,2,3,4,5,6,7,8,9 from m_appuser where ('a'<'b", "1 or 1<2", "1)--", "1)#",
            "1; drop table m_client", "-1 union all select password from m_appuser", "'a' or 'a'='a'", "'a'||(select password)", "(1)",
            "", " ", "1/**/or/**/1=1", "'a\\' or 1=1 or ''='", "'a\nb'", "id", "null" })
    void rejectsSqlInUnquotedPlaceholder(final String value) {
        assertThrows(SqlValidationException.class, () -> substitute(FULL_PARAMETER_LIST, Map.of("${reportListing}", value)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1", "-1", "10.5", "1,2,3", "'abc'", "'a', 'b'", "true", "'2024-01-01'", "'Branch #1'", "'a--b','c'",
            "'*/ or 1=1 /*'" })
    void acceptsLiteralsInUnquotedPlaceholder(final String value) {
        assertEquals("select 1 where x in (" + value + ")", substitute("select 1 where x in (${x})", Map.of("x", value)));
    }

    @Test
    void escapesSingleQuoteInsideQuotedPlaceholder() {
        final String sql = "select * from m_client c where c.display_name like concat('${name}', '%')";
        assertEquals("select * from m_client c where c.display_name like concat('x'') union all select password from m_appuser -- ', '%')",
                substitute(sql, Map.of("${name}", "x') union all select password from m_appuser -- ")));
    }

    @Test
    void escapesDoubleQuoteInsideDoubleQuotedPlaceholder() {
        final String sql = "select * from m_loan l where (l.currency_code = \"${currencyId}\" or \"-1\" = \"${currencyId}\")";
        assertEquals("select * from m_loan l where (l.currency_code = \"x\"\") or 1=1 -- \" or \"-1\" = \"x\"\") or 1=1 -- \")",
                substitute(sql, Map.of("${currencyId}", "x\") or 1=1 -- ")));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void rejectsBackslashInsideQuotedPlaceholder(final boolean mysql) {
        assertThrows(SqlValidationException.class,
                () -> ReportSqlParameterSubstitutor.substitute("select '${d}'", Map.of("d", "2023-01-01\\' OR 1=1 --"), mysql));
    }

    @Test
    void honoursEscapedQuotesInTemplate() {
        assertEquals("select 'it''s ${x}', 1", substitute("select 'it''s ${x}', ${y}", Map.of("y", "1")));
        assertEquals("select 'it''s a', 1", substitute("select 'it''s ${x}', ${y}", Map.of("x", "a", "y", "1")));
        assertEquals("select 'it\\'s a', 1",
                ReportSqlParameterSubstitutor.substitute("select 'it\\'s ${x}', ${y}", Map.of("x", "a", "y", "1"), true));
        assertThrows(SqlValidationException.class,
                () -> ReportSqlParameterSubstitutor.substitute("select E'it\\'s ${x}', ${y}", Map.of("x", "a", "y", "1 or 1=1"), false));
    }

    @Test
    void leavesPlaceholdersInCommentsAndUnknownPlaceholdersUntouched() {
        final String sql = "select 1 -- ${x}\n/* ${x} */ where a = ${y} and b = ${z}";
        assertEquals("select 1 -- ${x}\n/* ${x} */ where a = 1 and b = ${z}", substitute(sql, Map.of("x", "1 or 1=1", "y", "1")));
    }

    @Test
    void ignoresKeysThatDoNotNameAPlaceholder() {
        final String sql = "select 1 where a = ${officeId} and b = ${loanOfficerId}";
        assertEquals("select 1 where a = 1 and b = 2",
                substitute(sql, Map.of("${officeId} and b = ${loanOfficerId}", "0 or 1=1", "officeId", "1", "loanOfficerId", "2")));
    }

    @Test
    void doesNotRecurseIntoSubstitutedValues() {
        assertEquals("select '${y}', 1", substitute("select '${x}', ${y}", Map.of("x", "${y}", "y", "1")));
    }

    private static String substitute(final String sql, final Map<String, String> values) {
        return ReportSqlParameterSubstitutor.substitute(sql, values, false);
    }
}
