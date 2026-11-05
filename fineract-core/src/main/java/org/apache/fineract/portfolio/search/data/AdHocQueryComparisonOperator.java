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

import java.util.Arrays;

/**
 * Comparison operators accepted by the ad hoc search query. The SQL operator is a fixed literal so that request input is
 * never appended to the generated SQL.
 */
public enum AdHocQueryComparisonOperator {

    BETWEEN("between", "between"), //
    LESS_THAN_OR_EQUAL("<=", "<="), //
    GREATER_THAN_OR_EQUAL(">=", ">="), //
    LESS_THAN("<", "<"), //
    GREATER_THAN(">", ">"), //
    EQUAL("=", "=");

    private final String value;
    private final String sqlOperator;

    AdHocQueryComparisonOperator(final String value, final String sqlOperator) {
        this.value = value;
        this.sqlOperator = sqlOperator;
    }

    public String getValue() {
        return this.value;
    }

    public String getSqlOperator() {
        return this.sqlOperator;
    }

    public static Object[] allowedValues() {
        return Arrays.stream(values()).map(AdHocQueryComparisonOperator::getValue).toArray();
    }

    public static AdHocQueryComparisonOperator fromValue(final String value) {
        for (final AdHocQueryComparisonOperator operator : values()) {
            if (operator.value.equals(value)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("Unsupported ad hoc query comparison operator");
    }
}
