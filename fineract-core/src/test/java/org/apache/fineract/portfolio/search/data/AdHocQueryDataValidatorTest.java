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
package org.apache.fineract.portfolio.search.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdHocQueryDataValidatorTest {

    private static final String INJECTION = "> 0 union all select username, password, 1, 2, 3 from m_appuser where 1 >";

    private final AdHocQueryDataValidator validator = new AdHocQueryDataValidator(mock(SqlValidator.class), new FromJsonHelper());

    @Test
    void rejectsInjectedOutstandingAmountCondition() {
        final String json = "{\"entities\":[\"loans\"],\"locale\":\"en\",\"includeOutstandingAmount\":true,\"outstandingAmount\":0,"
                + "\"outstandingAmountCondition\":\"" + INJECTION + "\"}";

        final PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.retrieveSearchConditions(json));
        assertEquals("validation.msg.adHocQuery.outstandingAmountCondition.is.not.one.of.expected.enumerations",
                e.getErrors().get(0).getUserMessageGlobalisationCode());
    }

    @Test
    void rejectsInjectedOutStandingAmountPercentageCondition() {
        final String json = "{\"entities\":[\"loans\"],\"locale\":\"en\",\"includeOutStandingAmountPercentage\":true,"
                + "\"outStandingAmountPercentage\":0,\"outStandingAmountPercentageCondition\":\"" + INJECTION + "\"}";

        final PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.retrieveSearchConditions(json));
        assertEquals("validation.msg.adHocQuery.outStandingAmountPercentageCondition.is.not.one.of.expected.enumerations",
                e.getErrors().get(0).getUserMessageGlobalisationCode());
    }

    @Test
    void requiresConditionWhenOutstandingAmountFilterIsIncluded() {
        final String json = "{\"entities\":[\"loans\"],\"locale\":\"en\",\"includeOutstandingAmount\":true,\"outstandingAmount\":0}";

        final PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.retrieveSearchConditions(json));
        assertEquals("validation.msg.adHocQuery.outstandingAmountCondition.cannot.be.blank",
                e.getErrors().get(0).getUserMessageGlobalisationCode());
    }

    @ParameterizedTest
    @ValueSource(strings = { "<=", ">=", "<", ">", "=" })
    void acceptsAllowedComparisonOperators(final String condition) {
        final String json = "{\"entities\":[\"loans\"],\"locale\":\"en\",\"includeOutstandingAmount\":true,\"outstandingAmount\":100,"
                + "\"outstandingAmountCondition\":\"" + condition + "\",\"includeOutStandingAmountPercentage\":true,"
                + "\"outStandingAmountPercentage\":50,\"outStandingAmountPercentageCondition\":\"" + condition + "\"}";

        final AdHocQuerySearchConditions conditions = validator.retrieveSearchConditions(json);

        assertEquals(condition, AdHocQueryComparisonOperator.fromValue(conditions.getOutstandingAmountCondition()).getSqlOperator());
        assertEquals(condition,
                AdHocQueryComparisonOperator.fromValue(conditions.getOutStandingAmountPercentageCondition()).getSqlOperator());
    }

    @Test
    void acceptsBetweenCondition() {
        final String json = "{\"entities\":[\"loans\"],\"locale\":\"en\",\"includeOutstandingAmount\":true,\"minOutstandingAmount\":0,"
                + "\"maxOutstandingAmount\":600,\"outstandingAmountCondition\":\"between\"}";

        final AdHocQuerySearchConditions conditions = validator.retrieveSearchConditions(json);

        assertEquals(AdHocQueryComparisonOperator.BETWEEN,
                AdHocQueryComparisonOperator.fromValue(conditions.getOutstandingAmountCondition()));
    }

    @Test
    void comparisonOperatorRejectsUnknownValues() {
        assertThrows(IllegalArgumentException.class, () -> AdHocQueryComparisonOperator.fromValue(INJECTION));
        assertThrows(IllegalArgumentException.class, () -> AdHocQueryComparisonOperator.fromValue(null));
    }
}
