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

import java.util.Map;
import java.util.regex.Pattern;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;

/**
 * Substitutes {@code ${name}} placeholders in stored report and parameter SQL in a single pass.
 *
 * Trusted values (computed by the server) are inserted as-is. Request values are checked against the lexical context of
 * each placeholder so that they can only ever form a literal: inside a quoted literal the quote character is doubled
 * and backslashes are rejected; outside a literal only numbers, ISO dates, booleans, number lists or lists of
 * single-quoted literals are accepted.
 *
 * Once the SQL contains a construct whose meaning differs between MySQL and PostgreSQL ({@code #} or a backslash), the
 * lexical context of later placeholders cannot be trusted, so from there on only values that are safe in any context
 * (numbers, ISO dates, booleans and number lists) are accepted.
 */
public final class ReportSqlParameterSubstitutor {

    private static final Pattern UNQUOTED_VALUE = Pattern
            .compile("-?\\d+(\\.\\d+)?(\\s*,\\s*-?\\d+(\\.\\d+)?)*|\\d{4}-\\d{2}-\\d{2}|(?i:true|false)");
    private static final Pattern QUOTED_LITERAL_LIST = Pattern.compile("'[^'\\\\\\x00]*'(\\s*,\\s*'[^'\\\\\\x00]*')*");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+");

    private ReportSqlParameterSubstitutor() {}

    public static String substitute(final String sql, final Map<String, String> requestParams, final Map<String, String> trustedParams) {
        final int length = sql.length();
        final StringBuilder result = new StringBuilder(length);
        char quote = 0;
        boolean ambiguous = false;
        int i = 0;
        while (i < length) {
            final char c = sql.charAt(i);
            final char next = i + 1 < length ? sql.charAt(i + 1) : 0;
            if (quote == 0) {
                if (c == '-' && next == '-') {
                    final int end = indexOrLength(sql, "\n", i + 2, 0);
                    result.append(sql, i, end);
                    i = end;
                    continue;
                }
                if (c == '#') {
                    ambiguous = true;
                    final int end = indexOrLength(sql, "\n", i + 1, 0);
                    result.append(sql, i, end);
                    i = end;
                    continue;
                }
                if (c == '/' && next == '*') {
                    final int end = indexOrLength(sql, "*/", i + 2, 2);
                    result.append(sql, i, end);
                    i = end;
                    continue;
                }
                if (c == '\'' || c == '"' || c == '`') {
                    quote = c;
                    result.append(c);
                    i++;
                    continue;
                }
            } else if (c == '\\' && next != 0) {
                ambiguous = true;
                result.append(c).append(next);
                i += 2;
                continue;
            } else if (c == quote) {
                if (next == quote) {
                    result.append(c).append(next);
                    i += 2;
                } else {
                    quote = 0;
                    result.append(c);
                    i++;
                }
                continue;
            }

            if (c == '\\') {
                ambiguous = true;
            }
            if (c == '$' && next == '{') {
                final int end = sql.indexOf('}', i + 2);
                if (end > 0) {
                    final String key = sql.substring(i, end + 1);
                    if (trustedParams.containsKey(key)) {
                        result.append(trustedParams.get(key));
                        i = end + 1;
                        continue;
                    }
                    if (requestParams.containsKey(key)) {
                        final String fragment = toSqlFragment(key, requestParams.get(key), quote, ambiguous);
                        if (fragment.startsWith("-") && !result.isEmpty() && result.charAt(result.length() - 1) == '-') {
                            result.append(' ');
                        }
                        result.append(fragment);
                        i = end + 1;
                        continue;
                    }
                }
            }
            result.append(c);
            i++;
        }
        return result.toString();
    }

    private static int indexOrLength(final String sql, final String token, final int from, final int tokenLength) {
        final int index = sql.indexOf(token, from);
        return index < 0 ? sql.length() : index + tokenLength;
    }

    private static String toSqlFragment(final String key, final String rawValue, final char quote, final boolean ambiguous) {
        final String value = rawValue == null ? "" : rawValue;
        final boolean valid;
        if (ambiguous) {
            valid = UNQUOTED_VALUE.matcher(value).matches();
        } else {
            valid = switch (quote) {
                case '\'', '"' -> value.indexOf('\\') < 0 && value.indexOf('\0') < 0;
                case '`' -> NUMBER.matcher(value).matches();
                default -> UNQUOTED_VALUE.matcher(value).matches() || QUOTED_LITERAL_LIST.matcher(value).matches();
            };
        }
        if (!valid) {
            final String parameterName = "R_" + key.substring(2, key.length() - 1);
            throw new PlatformApiDataValidationException("validation.msg.report.parameter.value.invalid",
                    "Invalid value for report parameter " + parameterName, parameterName);
        }
        if (!ambiguous && (quote == '\'' || quote == '"')) {
            return value.replace(String.valueOf(quote), String.valueOf(quote) + quote);
        }
        return value;
    }
}
