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
package org.apache.fineract.infrastructure.core.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.database.DatabaseType;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DatatableCommandFromApiJsonDeserializerTest {

    private static final String INJECTED_AFTER = "id`, RENAME TO `zz` -- ";

    private DatatableCommandFromApiJsonDeserializer underTest;

    @BeforeEach
    void setUp() {
        DatabaseTypeResolver databaseTypeResolver = Mockito.mock(DatabaseTypeResolver.class);
        Mockito.when(databaseTypeResolver.databaseType()).thenReturn(DatabaseType.MYSQL);
        underTest = new DatatableCommandFromApiJsonDeserializer(new FromJsonHelper(), databaseTypeResolver);
    }

    @Test
    void validateForUpdateRejectsNonIdentifierAfterInAddColumns() {
        String json = "{\"apptableName\":\"m_client\",\"addColumns\":[{\"name\":\"c1\",\"type\":\"String\",\"length\":10,\"after\":\""
                + INJECTED_AFTER + "\"}]}";

        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateForUpdate(json));
        assertTrue(exception.getErrors().stream().anyMatch(error -> "after".equals(error.getParameterName())));
    }

    @Test
    void validateForUpdateRejectsNonIdentifierAfterInChangeColumns() {
        String json = "{\"apptableName\":\"m_client\",\"changeColumns\":[{\"name\":\"c1\",\"after\":\"" + INJECTED_AFTER + "\"}]}";

        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateForUpdate(json));
        assertTrue(exception.getErrors().stream().anyMatch(error -> "after".equals(error.getParameterName())));
    }

    @Test
    void validateForUpdateRejectsAfterLongerThanColumnNameLimit() {
        String json = "{\"apptableName\":\"m_client\",\"changeColumns\":[{\"name\":\"c1\",\"after\":\"" + "a".repeat(51) + "\"}]}";

        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateForUpdate(json));
        assertTrue(exception.getErrors().stream().anyMatch(error -> "after".equals(error.getParameterName())));
    }

    @Test
    void validateForUpdateAcceptsColumnNameAsAfter() {
        String json = "{\"apptableName\":\"m_client\",\"entitySubType\":\"Person\","
                + "\"addColumns\":[{\"name\":\"c1\",\"type\":\"String\",\"length\":10,\"after\":\"id\"}],"
                + "\"changeColumns\":[{\"name\":\"c2\",\"after\":\"c1\"}]}";

        assertDoesNotThrow(() -> underTest.validateForUpdate(json));
    }
}
