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
package org.apache.fineract.adhocquery.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdHocDataValidatorTest {

    private final AdHocDataValidator validator = new AdHocDataValidator(new FromJsonHelper());

    private static String createJson(final String tableName, final String tableFields) {
        return "{\"name\":\"report\",\"query\":\"select id, display_name from m_client\",\"tableName\":\"" + tableName
                + "\",\"tableFields\":\"" + tableFields + "\"}";
    }

    @Test
    void createAcceptsPlainIdentifiers() {
        assertDoesNotThrow(() -> validator.validateForCreate(createJson("m_adhoc_client", "client_id, display_name")));
        assertDoesNotThrow(() -> validator.validateForCreate(createJson("_tmp1", "a,b ,  c")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "m_appuser (id) select 1; drop table m_client", "m_client x", "m_client;", "1table", "schema.table",
            "`m_client`" })
    void createRejectsTableNameThatIsNotAnIdentifier(final String tableName) {
        PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreate(createJson(tableName, "id")));
        assertEquals("tableName", e.getErrors().get(0).getParameterName());
    }

    @ParameterizedTest
    @ValueSource(strings = { "id) select 1; --", "id,", "id name", "id, (select 1)", "" })
    void createRejectsTableFieldsThatAreNotAnIdentifierList(final String tableFields) {
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(createJson("m_client", tableFields)));
    }

    @Test
    void updateRejectsTableNameWithSpaceOrSemicolon() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate("{\"tableName\":\"m_appuser (username) select 'x'; --\"}"));
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForUpdate("{\"tableName\":\"m_client;\"}"));
    }

    @Test
    void updateRejectsTableFieldsThatAreNotAnIdentifierList() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate("{\"tableFields\":\"id) select 1; delete from m_client; --\"}"));
    }

    @Test
    void updateAcceptsPlainIdentifiers() {
        assertDoesNotThrow(() -> validator.validateForUpdate("{\"tableName\":\"m_adhoc_client\",\"tableFields\":\"client_id, name\"}"));
        assertDoesNotThrow(() -> validator.validateForUpdate("{\"name\":\"renamed\"}"));
    }
}
