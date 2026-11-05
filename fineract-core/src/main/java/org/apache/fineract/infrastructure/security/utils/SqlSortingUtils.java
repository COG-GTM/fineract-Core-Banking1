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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;

/**
 * Resolves user supplied {@code orderBy} / {@code sortOrder} request parameters to trusted SQL fragments. Only values
 * present in an explicit allowlist are accepted, so request input is never concatenated into SQL.
 */
public final class SqlSortingUtils {

    private static final Set<String> SORT_ORDER_VALUES = Set.of("ASC", "DESC");

    private SqlSortingUtils() {}

    /**
     * Builds a case-insensitive allowlist from API field name to SQL column expression.
     */
    public static Map<String, String> sortableColumns(Map<String, String> apiFieldToColumn) {
        Map<String, String> columns = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        columns.putAll(apiFieldToColumn);
        return Collections.unmodifiableMap(columns);
    }

    /**
     * @return the SQL column mapped to {@code orderBy}, or {@code null} when {@code orderBy} is blank
     * @throws PlatformApiDataValidationException
     *             when {@code orderBy} is not in the allowlist
     */
    public static String resolveOrderBy(String resourceName, String orderBy, Map<String, String> sortableColumns) {
        if (StringUtils.isBlank(orderBy)) {
            return null;
        }
        String column = sortableColumns.get(orderBy.trim());
        if (column == null) {
            throw new PlatformApiDataValidationException(
                    List.of(ApiParameterError.parameterErrorWithValue("validation.msg." + resourceName + ".orderBy.value.is.not.supported",
                            "The orderBy value is not supported. The supported orderBy values are " + sortableColumns.keySet(), "orderBy",
                            orderBy)));
        }
        return column;
    }

    /**
     * @return {@code ASC} or {@code DESC}, or {@code null} when {@code sortOrder} is blank
     * @throws PlatformApiDataValidationException
     *             when {@code sortOrder} is neither {@code ASC} nor {@code DESC} (case-insensitive)
     */
    public static String resolveSortOrder(String resourceName, String sortOrder) {
        if (StringUtils.isBlank(sortOrder)) {
            return null;
        }
        String normalized = sortOrder.trim().toUpperCase(Locale.ROOT);
        if (!SORT_ORDER_VALUES.contains(normalized)) {
            throw new PlatformApiDataValidationException(List.of(ApiParameterError.parameterErrorWithValue(
                    "validation.msg." + resourceName + ".sortOrder.value.is.not.supported",
                    "The sortOrder value is not supported. The supported sortOrder values are [ASC, DESC]", "sortOrder", sortOrder)));
        }
        return normalized;
    }
}
