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
import org.apache.fineract.infrastructure.security.exception.SqlValidationException;

/**
 * Substitutes <code>${name}</code> placeholders in stretchy report SQL in a single pass, rendering each user supplied value
 * according to the SQL context the placeholder appears in, so that a value can never terminate a string literal or add SQL
 * syntax of its own.
 * <ul>
 * <li>inside a '...' or "..." literal: the enclosing quote is doubled, backslashes are rejected</li>
 * <li>inside a `...` identifier: only <code>[A-Za-z0-9_]</code> is accepted</li>
 * <li>unquoted: only a number or a comma separated list of numbers and '...' literals is accepted</li>
 * </ul>
 * Trusted values are substituted verbatim. Placeholders inside comments are left untouched.
 */
public final class ReportSqlParameterBinder {

    private static final String UNQUOTED_ITEM = "(?:-?\\d+(?:\\.\\d+)?|'(?:[^'\\\\]|'')*+')";
    private static final Pattern UNQUOTED_VALUE = Pattern
            .compile("[ \\t]*+" + UNQUOTED_ITEM + "(?:[ \\t]*+,[ \\t]*+" + UNQUOTED_ITEM + ")*+[ \\t]*+");
    private static final Pattern IDENTIFIER_VALUE = Pattern.compile("[A-Za-z0-9_]+");

    private enum Context {
        UNQUOTED, SINGLE_QUOTED, DOUBLE_QUOTED, BACKTICK_QUOTED, LINE_COMMENT, BLOCK_COMMENT
    }

    private ReportSqlParameterBinder() {}

    /**
     * @param sql
     *            report SQL template
     * @param userParams
     *            untrusted values keyed by <code>${name}</code>
     * @param trustedParams
     *            server generated values keyed by <code>${name}</code>; these win over user values with the same key
     */
    public static String bind(final String sql, final Map<String, String> userParams, final Map<String, String> trustedParams) {
        final StringBuilder out = new StringBuilder(sql.length() + 64);
        final int length = sql.length();
        Context context = Context.UNQUOTED;
        int i = 0;
        while (i < length) {
            final char c = sql.charAt(i);
            final char next = i + 1 < length ? sql.charAt(i + 1) : '\0';

            if (c == '$' && next == '{' && context != Context.LINE_COMMENT && context != Context.BLOCK_COMMENT) {
                final int end = sql.indexOf('}', i + 2);
                if (end > 0) {
                    final String key = sql.substring(i, end + 1);
                    if (trustedParams.containsKey(key)) {
                        out.append(trustedParams.get(key));
                        i = end + 1;
                        continue;
                    }
                    if (userParams.containsKey(key)) {
                        out.append(render(key, userParams.get(key), context));
                        i = end + 1;
                        continue;
                    }
                }
            }

            out.append(c);
            i++;
            switch (context) {
                case UNQUOTED -> {
                    if (c == '\'') {
                        context = Context.SINGLE_QUOTED;
                    } else if (c == '"') {
                        context = Context.DOUBLE_QUOTED;
                    } else if (c == '`') {
                        context = Context.BACKTICK_QUOTED;
                    } else if (c == '#' || (c == '-' && next == '-')) {
                        context = Context.LINE_COMMENT;
                    } else if (c == '/' && next == '*') {
                        out.append(next);
                        i++;
                        context = Context.BLOCK_COMMENT;
                    }
                }
                case SINGLE_QUOTED, DOUBLE_QUOTED -> {
                    if (c == '\\' && i < length) {
                        out.append(sql.charAt(i));
                        i++;
                    } else if ((c == '\'' && context == Context.SINGLE_QUOTED) || (c == '"' && context == Context.DOUBLE_QUOTED)) {
                        context = Context.UNQUOTED;
                    }
                }
                case BACKTICK_QUOTED -> {
                    if (c == '`') {
                        context = Context.UNQUOTED;
                    }
                }
                case LINE_COMMENT -> {
                    if (c == '\n' || c == '\r') {
                        context = Context.UNQUOTED;
                    }
                }
                case BLOCK_COMMENT -> {
                    if (c == '*' && next == '/') {
                        out.append(next);
                        i++;
                        context = Context.UNQUOTED;
                    }
                }
            }
        }
        return out.toString();
    }

    private static String render(final String key, final String value, final Context context) {
        final String safeValue = value == null ? "" : value;
        switch (context) {
            case SINGLE_QUOTED, DOUBLE_QUOTED -> {
                if (safeValue.indexOf('\\') >= 0 || safeValue.indexOf('\0') >= 0) {
                    throw invalid(key);
                }
                return context == Context.SINGLE_QUOTED ? safeValue.replace("'", "''") : safeValue.replace("\"", "\"\"");
            }
            case BACKTICK_QUOTED -> {
                if (!IDENTIFIER_VALUE.matcher(safeValue).matches()) {
                    throw invalid(key);
                }
                return safeValue;
            }
            default -> {
                if (!UNQUOTED_VALUE.matcher(safeValue).matches()) {
                    throw invalid(key);
                }
                return safeValue;
            }
        }
    }

    private static SqlValidationException invalid(final String key) {
        return new SqlValidationException(String.format("invalid value for report parameter '%s'", key));
    }
}
