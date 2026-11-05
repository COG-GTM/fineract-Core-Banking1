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
package org.apache.fineract.organisation.office.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;

public final class OfficeSearchSort {

    private static final Map<String, String> ORDER_BY_COLUMNS = orderByColumns();
    private static final String ALLOWED_ORDER_BY = "id, name, externalId, openingDate, hierarchy, parentId, parentName";
    private static final List<String> SORT_ORDERS = List.of("ASC", "DESC");

    private OfficeSearchSort() {}

    public static void validate(final String orderBy, final String sortOrder) {
        if (orderBy != null && !orderBy.isBlank()) {
            orderByColumn(orderBy);
        }
        if (sortOrder != null && !sortOrder.isBlank()) {
            sortOrder(sortOrder);
        }
    }

    public static String orderByColumn(final String orderBy) {
        final String column = ORDER_BY_COLUMNS.get(orderBy.trim().toLowerCase(Locale.ROOT));
        if (column == null) {
            throw invalidSearchParameter("orderBy", "Unsupported orderBy value. Allowed values: " + ALLOWED_ORDER_BY + ".", orderBy);
        }
        return column;
    }

    public static String sortOrder(final String sortOrder) {
        final String normalized = sortOrder.trim().toUpperCase(Locale.ROOT);
        if (!SORT_ORDERS.contains(normalized)) {
            throw invalidSearchParameter("sortOrder", "Unsupported sortOrder value. Allowed values: ASC, DESC.", sortOrder);
        }
        return normalized;
    }

    private static Map<String, String> orderByColumns() {
        final Map<String, String> columns = new HashMap<>();
        addOrderByColumn(columns, "o.id", "id");
        addOrderByColumn(columns, "o.name", "name");
        addOrderByColumn(columns, "o.external_id", "externalId", "external_id");
        addOrderByColumn(columns, "o.opening_date", "openingDate", "opening_date");
        addOrderByColumn(columns, "o.hierarchy", "hierarchy");
        addOrderByColumn(columns, "o.parent_id", "parentId", "parent_id", "parent.id");
        addOrderByColumn(columns, "parent.name", "parentName");
        return Map.copyOf(columns);
    }

    private static void addOrderByColumn(final Map<String, String> columns, final String column, final String... aliases) {
        columns.put(column, column);
        for (final String alias : aliases) {
            columns.put(alias.toLowerCase(Locale.ROOT), column);
        }
    }

    private static PlatformApiDataValidationException invalidSearchParameter(final String parameterName, final String defaultUserMessage,
            final String value) {
        final ApiParameterError error = ApiParameterError.parameterError("validation.msg.office." + parameterName + ".is.not.supported",
                defaultUserMessage, parameterName, value);
        return new PlatformApiDataValidationException(List.of(error));
    }
}
