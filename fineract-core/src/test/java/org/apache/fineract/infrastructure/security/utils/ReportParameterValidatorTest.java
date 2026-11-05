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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportParameterValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = { "", "1", "-1", "-10", "0", "2013-04-16", "2013-04-16 10:15:30", "2013-04-16T10:15", "USD", "1,2,3",
            "1.5", "00010001", "Fund", "'Active Loans'", "'Active Loans', 'Client Listing'", "'Pr\u00e9stamos activos'" })
    void acceptsLegitimateValues(String value) {
        assertThat(ReportParameterValidator.isValidValue(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "0) union all (select username, password from m_appuser",
            "0 union all select username, password from m_appuser",
            "0 or exists(select 1 from m_appuser)", "1 or 1=1", "1;drop table m_appuser", "1--", "1-- ", "1/**/or/**/1=1", "1-",
            "1/", "' or '1'='1", "'a' or 'b'", "'a''", "'a\\'", "'a\"'", "1 #", "(select 1)", "${currentUserId}", "1\n", "a b",
            "'a'||'b'", "'a';select 1" })
    void rejectsInjectionAttempts(String value) {
        assertThat(ReportParameterValidator.isValidValue(value)).isFalse();
        assertThatThrownBy(() -> ReportParameterValidator.validate("officeId", value))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "office Id", "officeId}", "a${b", "x)" })
    void rejectsInvalidNames(String name) {
        assertThatThrownBy(() -> ReportParameterValidator.validate(name, "1")).isInstanceOf(PlatformApiDataValidationException.class);
    }
}
