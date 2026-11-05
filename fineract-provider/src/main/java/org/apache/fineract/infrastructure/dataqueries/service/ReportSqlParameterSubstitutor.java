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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.fineract.infrastructure.security.exception.SqlValidationException;

/**
 * Substitutes caller supplied values into the <code>${name}</code> placeholders of a stored report / parameter SQL.
 *
 * The stored SQL is tokenized so that each placeholder is rendered according to where it sits:
 * <ul>
 * <li>inside a quoted literal or identifier: the value is escaped for that quote character (backslashes and NUL are
 * rejected, so the escaping is safe whatever the database string escaping mode is)</li>
 * <li>outside quotes: the value must be a number, a boolean, a single quoted string literal, or a comma separated list
 * of those (e.g. <code>'Client Listing','Loan Listing'</code> for <code>in(${reportListing})</code>); anything else is
 * rejected</li>
 * <li>inside a comment: the placeholder is left untouched</li>
 * </ul>
 * Placeholders without a value are left untouched. Values are never interpreted as SQL syntax.
 */
public final class ReportSqlParameterSubstitutor {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}'\"`\\s]+)}");

    private static final String NUMBER = "[+-]?\\d+(?:\\.\\d+)?";
    private static final String STRING_LITERAL = "'(?:[^'\\\\\\r\\n\\u0000]|'')*'";
    private static final String ATOM = "(?:" + NUMBER + "|" + STRING_LITERAL + "|true|false)";
    private static final Pattern UNQUOTED_VALUE = Pattern.compile("\\s*" + ATOM + "\\s*(?:,\\s*" + ATOM + "\\s*)*",
            Pattern.CASE_INSENSITIVE);

    private ReportSqlParameterSubstitutor() {}

    /**
     * @param sql
     *            the stored (trusted) SQL template
     * @param values
     *            placeholder values keyed by placeholder name, either as <code>name</code> or <code>${name}</code>
     * @param mysqlSyntax
     *            true for MySQL / MariaDB (backslash escapes inside quoted literals, <code>#</code> line comments)
     * @return the SQL with the provided placeholders substituted
     * @throws SqlValidationException
     *             when a value is not allowed in the context of its placeholder
     */
    public static String substitute(final String sql, final Map<String, String> values, final boolean mysqlSyntax) {
        if (sql == null || values == null || values.isEmpty() || !sql.contains("${")) {
            return sql;
        }
        final Map<String, String> valuesByName = normalize(values);
        final StringBuilder result = new StringBuilder(sql.length() + 64);
        final Matcher placeholder = PLACEHOLDER.matcher(sql);
        final int length = sql.length();
        int i = 0;
        while (i < length) {
            final char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                final boolean backslashEscapes = c != '`' && (mysqlSyntax || (c == '\'' && isPostgresEscapeString(sql, i)));
                i = copyQuoted(sql, i, c, backslashEscapes, valuesByName, placeholder, result);
            } else if ((c == '-' && i + 1 < length && sql.charAt(i + 1) == '-') || (mysqlSyntax && c == '#')) {
                final int end = sql.indexOf('\n', i);
                final int stop = end < 0 ? length : end;
                result.append(sql, i, stop);
                i = stop;
            } else if (c == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                final int end = sql.indexOf("*/", i + 2);
                final int stop = end < 0 ? length : end + 2;
                result.append(sql, i, stop);
                i = stop;
            } else if (c == '$' && placeholder.region(i, length).lookingAt() && valuesByName.containsKey(placeholder.group(1))) {
                final String name = placeholder.group(1);
                result.append(unquotedValue(name, valuesByName.get(name)));
                i = placeholder.end();
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    private static int copyQuoted(final String sql, final int start, final char quote, final boolean backslashEscapes,
            final Map<String, String> valuesByName, final Matcher placeholder, final StringBuilder result) {
        final int length = sql.length();
        result.append(quote);
        int i = start + 1;
        while (i < length) {
            final char c = sql.charAt(i);
            if (c == quote) {
                if (i + 1 < length && sql.charAt(i + 1) == quote) {
                    result.append(quote).append(quote);
                    i += 2;
                    continue;
                }
                result.append(quote);
                return i + 1;
            }
            if (backslashEscapes && c == '\\' && i + 1 < length) {
                result.append(c).append(sql.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '$' && placeholder.region(i, length).lookingAt() && valuesByName.containsKey(placeholder.group(1))) {
                final String name = placeholder.group(1);
                result.append(quotedValue(name, valuesByName.get(name), quote));
                i = placeholder.end();
                continue;
            }
            result.append(c);
            i++;
        }
        return length;
    }

    private static boolean isPostgresEscapeString(final String sql, final int quoteIndex) {
        if (quoteIndex == 0) {
            return false;
        }
        final char prefix = sql.charAt(quoteIndex - 1);
        if (prefix != 'E' && prefix != 'e') {
            return false;
        }
        return quoteIndex < 2 || (!Character.isLetterOrDigit(sql.charAt(quoteIndex - 2)) && sql.charAt(quoteIndex - 2) != '_');
    }

    private static String quotedValue(final String name, final String value, final char quote) {
        if (value == null) {
            return "";
        }
        if (value.indexOf('\\') >= 0 || value.indexOf('\u0000') >= 0) {
            throw invalid(name);
        }
        final String q = String.valueOf(quote);
        return value.replace(q, q + q);
    }

    private static String unquotedValue(final String name, final String value) {
        if (value == null || !UNQUOTED_VALUE.matcher(value).matches()) {
            throw invalid(name);
        }
        return value;
    }

    private static SqlValidationException invalid(final String name) {
        return new SqlValidationException(String.format("invalid value for report parameter '%s'", name));
    }

    private static Map<String, String> normalize(final Map<String, String> values) {
        final Map<String, String> valuesByName = new HashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            if (key.startsWith("${") && key.endsWith("}")) {
                key = key.substring(2, key.length() - 1);
            }
            valuesByName.put(key, entry.getValue());
        }
        return valuesByName;
    }
}
