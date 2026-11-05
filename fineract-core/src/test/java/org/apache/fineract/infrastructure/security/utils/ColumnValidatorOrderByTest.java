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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ColumnValidatorOrderByTest {

    private final ColumnValidator columnValidator = new ColumnValidator(null, null);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "id", "displayName", "c.display_name", "journalEntry.id", "_col1" })
    void acceptsPlainColumnReferences(String orderBy) {
        assertDoesNotThrow(() -> columnValidator.validateOrderBy(orderBy));
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select sleep(5))", "if(strcmp(substring((select 1),1,1),'a'),c.id,c.name)", "c.id, c.name", "c.id desc",
            "case when 1 then c.id end", "a.b.c", "1", "c.id\n", "c.id;drop table x" })
    void rejectsExpressions(String orderBy) {
        assertThrows(PlatformApiDataValidationException.class, () -> columnValidator.validateOrderBy(orderBy));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "ASC", "DESC", "asc", "desc" })
    void acceptsAscOrDesc(String sortOrder) {
        assertDoesNotThrow(() -> columnValidator.validateSortOrder(sortOrder));
    }

    @ParameterizedTest
    @ValueSource(strings = { "(select sleep(5))", "ASC, c.id", "desc limit 1", "ASCENDING" })
    void rejectsOtherSortOrders(String sortOrder) {
        assertThrows(PlatformApiDataValidationException.class, () -> columnValidator.validateSortOrder(sortOrder));
    }
}
