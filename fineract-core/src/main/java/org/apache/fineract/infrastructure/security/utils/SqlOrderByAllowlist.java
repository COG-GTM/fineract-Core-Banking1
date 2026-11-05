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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.SearchParameters;

/**
 * Builds an {@code ORDER BY} clause only from a fixed set of sortable columns, so user supplied {@code orderBy} and
 * {@code sortOrder} values never reach the SQL string.
 */
public final class SqlOrderByAllowlist {

    private final Map<String, String> columnsByLookupKey;

    /**
     * @param sortableColumns
     *            response field alias to qualified SQL column; both the alias and the qualified column are accepted
     *            (case-insensitive) as {@code orderBy} values
     */
    public SqlOrderByAllowlist(final Map<String, String> sortableColumns) {
        final Map<String, String> lookup = new HashMap<>();
        sortableColumns.forEach((alias, column) -> {
            lookup.put(alias.toLowerCase(Locale.ROOT), column);
            lookup.put(column.toLowerCase(Locale.ROOT), column);
        });
        this.columnsByLookupKey = Map.copyOf(lookup);
    }

    /**
     * @return {@code " order by <column> [ASC|DESC]"}, or an empty string when no {@code orderBy} was requested
     * @throws PlatformApiDataValidationException
     *             if {@code orderBy} is not an allowed column or {@code sortOrder} is not {@code ASC}/{@code DESC}
     */
    public String orderByClause(final SearchParameters searchParameters) {
        if (searchParameters == null) {
            return "";
        }
        final String sortOrder = resolveSortOrder(searchParameters);
        if (!searchParameters.hasOrderBy()) {
            return "";
        }
        final String orderBy = searchParameters.getOrderBy();
        final String column = this.columnsByLookupKey.get(orderBy.trim().toLowerCase(Locale.ROOT));
        if (column == null) {
            throw new PlatformApiDataValidationException("validation.msg.orderBy.not.supported", "Unsupported orderBy value", "orderBy",
                    orderBy);
        }
        return sortOrder == null ? " order by " + column : " order by " + column + " " + sortOrder;
    }

    private static String resolveSortOrder(final SearchParameters searchParameters) {
        if (!searchParameters.hasSortOrder()) {
            return null;
        }
        final String sortOrder = searchParameters.getSortOrder().trim().toUpperCase(Locale.ROOT);
        if (!"ASC".equals(sortOrder) && !"DESC".equals(sortOrder)) {
            throw new PlatformApiDataValidationException("validation.msg.sortOrder.not.supported", "sortOrder must be ASC or DESC",
                    "sortOrder", searchParameters.getSortOrder());
        }
        return sortOrder;
    }
}
