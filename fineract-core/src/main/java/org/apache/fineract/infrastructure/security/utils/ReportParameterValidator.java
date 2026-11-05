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

import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;

/**
 * Allowlist validation for user supplied report parameters that are substituted into stored report SQL.
 *
 * <p>
 * Accepted values are either a single token made of ASCII letters, digits and the separators {@code _ . : , - /} (ids,
 * codes, dates and comma separated id lists), a date-time, or a comma separated list of single-quoted literals that
 * contain no quotes, backslashes or control characters (e.g. {@code 'Active Loans'}). None of these can contain
 * whitespace outside a literal, parentheses, comments or statement terminators, so a value cannot add SQL clauses
 * regardless of where the placeholder sits in the report SQL.
 */
public final class ReportParameterValidator {

    private static final Pattern PARAMETER_NAME = Pattern.compile("^[A-Za-z0-9_]{1,100}$");
    private static final Pattern SIMPLE_VALUE = Pattern.compile("^-?[A-Za-z0-9_.:,]+(?:[-/][A-Za-z0-9_.:,]+)*$");
    private static final Pattern DATE_TIME_VALUE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?$");
    private static final String LITERAL = "'[^'\"`\\\\\\p{Cntrl}]*'";
    private static final Pattern QUOTED_LITERAL_LIST = Pattern.compile("^" + LITERAL + "(?: *, *" + LITERAL + ")*$");

    private ReportParameterValidator() {}

    public static boolean isValidName(final String name) {
        return name != null && PARAMETER_NAME.matcher(name).matches();
    }

    public static boolean isValidValue(final String value) {
        if (StringUtils.isEmpty(value)) {
            return true;
        }
        return SIMPLE_VALUE.matcher(value).matches() || DATE_TIME_VALUE.matcher(value).matches()
                || QUOTED_LITERAL_LIST.matcher(value).matches();
    }

    public static void validate(final String name, final String value) {
        if (!isValidName(name)) {
            final String parameter = "R_" + LogParameterEscapeUtil.escapeLogParameter(String.valueOf(name));
            throw new PlatformApiDataValidationException("validation.msg.report.parameter.name.invalid", "Invalid name", parameter);
        }
        if (!isValidValue(value)) {
            final String parameter = "R_" + name;
            throw new PlatformApiDataValidationException("validation.msg.report.parameter.value.invalid", "Invalid value", parameter);
        }
    }
}
