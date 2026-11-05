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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;

/**
 * Maps user supplied {@code orderBy} / {@code sortOrder} request parameters onto a fixed set of SQL sort columns, so
 * request text is never concatenated into an {@code ORDER BY} clause.
 */
public final class OrderByAllowlist {

    public static final String ORDER_BY_PARAMETER = "orderBy";
    public static final String SORT_ORDER_PARAMETER = "sortOrder";

    private final Map<String, String> sqlColumnsByName;

    private OrderByAllowlist(Map<String, String> sqlColumnsByName) {
        this.sqlColumnsByName = sqlColumnsByName;
    }

    /**
     * @param sqlColumnsByName
     *            accepted {@code orderBy} names mapped to the trusted SQL column to sort by; the SQL column itself is
     *            also accepted. Matching is case-insensitive.
     */
    public static OrderByAllowlist of(Map<String, String> sqlColumnsByName) {
        Map<String, String> normalized = new LinkedHashMap<>();
        sqlColumnsByName.forEach((name, column) -> {
            normalized.put(name.toLowerCase(Locale.ROOT), column);
            normalized.put(column.toLowerCase(Locale.ROOT), column);
        });
        return new OrderByAllowlist(Collections.unmodifiableMap(normalized));
    }

    /**
     * @return the trusted SQL column for {@code orderBy}
     * @throws PlatformApiDataValidationException
     *             if {@code orderBy} is not an allowed value
     */
    public String resolveColumn(String orderBy) {
        String column = orderBy == null ? null : sqlColumnsByName.get(orderBy.trim().toLowerCase(Locale.ROOT));
        if (column == null) {
            throw new PlatformApiDataValidationException("validation.msg.orderBy.not.supported",
                    "The orderBy parameter must be one of: " + String.join(", ", sqlColumnsByName.keySet()), ORDER_BY_PARAMETER, orderBy);
        }
        return column;
    }

    /**
     * @return {@code ASC} or {@code DESC}
     * @throws PlatformApiDataValidationException
     *             if {@code sortOrder} is neither ASC nor DESC (case-insensitive)
     */
    public static String resolveSortOrder(String sortOrder) {
        String normalized = sortOrder == null ? "" : sortOrder.trim().toUpperCase(Locale.ROOT);
        if (!"ASC".equals(normalized) && !"DESC".equals(normalized)) {
            throw new PlatformApiDataValidationException("validation.msg.sortOrder.not.supported",
                    "The sortOrder parameter must be ASC or DESC", SORT_ORDER_PARAMETER, sortOrder);
        }
        return normalized;
    }

    /**
     * Builds {@code " order by <column> [ASC|DESC]"} from allowed values only. Returns an empty string when
     * {@code orderBy} is blank; a non-blank {@code sortOrder} is validated even then.
     */
    public String toOrderByClause(String orderBy, String sortOrder) {
        String direction = StringUtils.isBlank(sortOrder) ? null : resolveSortOrder(sortOrder);
        if (StringUtils.isBlank(orderBy)) {
            return "";
        }
        String clause = " order by " + resolveColumn(orderBy);
        return direction == null ? clause : clause + " " + direction;
    }
}
