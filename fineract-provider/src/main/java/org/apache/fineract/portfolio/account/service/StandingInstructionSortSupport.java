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
package org.apache.fineract.portfolio.account.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.SearchParameters;

/**
 * Builds the ORDER BY clause for standing instruction list queries from a fixed allowlist, so request values are never
 * concatenated into SQL.
 */
final class StandingInstructionSortSupport {

    private StandingInstructionSortSupport() {}

    /**
     * Returns {@code " order by <column> [ASC|DESC]"} or an empty string when no orderBy is requested.
     *
     * @param sortableColumns
     *            map of lower-case API sort keys to trusted SQL column expressions
     * @throws PlatformApiDataValidationException
     *             if orderBy is not an allowed key or sortOrder is not ASC/DESC
     */
    static String orderByClause(final SearchParameters searchParameters, final Map<String, String> sortableColumns,
            final String resourceName) {
        final List<ApiParameterError> errors = new ArrayList<>();
        String column = null;
        if (searchParameters.hasOrderBy()) {
            column = sortableColumns.get(searchParameters.getOrderBy().trim().toLowerCase(Locale.ROOT));
            if (column == null) {
                final String supported = new TreeSet<>(sortableColumns.keySet()).toString();
                errors.add(ApiParameterError.parameterError("validation.msg." + resourceName + ".orderBy.value.is.not.supported",
                        "The orderBy value is not supported. The supported orderBy values are " + supported, "orderBy",
                        searchParameters.getOrderBy(), supported));
            }
        }

        String direction = null;
        if (searchParameters.hasSortOrder()) {
            final String sortOrder = searchParameters.getSortOrder().trim().toUpperCase(Locale.ROOT);
            if ("ASC".equals(sortOrder) || "DESC".equals(sortOrder)) {
                direction = sortOrder;
            } else {
                errors.add(ApiParameterError.parameterError("validation.msg." + resourceName + ".sortOrder.value.is.not.supported",
                        "The sortOrder value is not supported. The supported sortOrder values are [ASC, DESC]", "sortOrder",
                        searchParameters.getSortOrder(), "[ASC, DESC]"));
            }
        }

        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
        if (column == null) {
            return "";
        }
        return direction == null ? " order by " + column : " order by " + column + " " + direction;
    }
}
