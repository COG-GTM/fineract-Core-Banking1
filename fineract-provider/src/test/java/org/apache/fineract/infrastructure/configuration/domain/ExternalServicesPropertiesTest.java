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
package org.apache.fineract.infrastructure.configuration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.apache.fineract.infrastructure.configuration.service.ExternalServicesConstants;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;

class ExternalServicesPropertiesTest {

    private static final String S3_SECRET = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";

    @Test
    void maskedPlaceholderDoesNotOverwriteStoredSecret() {
        ExternalServicesProperties property = new ExternalServicesProperties().setValue(S3_SECRET);

        Map<String, Object> changes = property.update(
                command(ExternalServicesConstants.S3_SECRET_KEY, ExternalServicesConstants.MASKED_SECRET_VALUE),
                ExternalServicesConstants.S3_SECRET_KEY);

        assertTrue(changes.isEmpty());
        assertEquals(S3_SECRET, property.getValue());
    }

    @Test
    void maskedPlaceholderDoesNotPopulateUnsetSecret() {
        ExternalServicesProperties property = new ExternalServicesProperties();

        Map<String, Object> changes = property.update(
                command(ExternalServicesConstants.SMS_TENANT_APP_KEY, ExternalServicesConstants.MASKED_SECRET_VALUE),
                ExternalServicesConstants.SMS_TENANT_APP_KEY);

        assertTrue(changes.isEmpty());
        assertNull(property.getValue());
    }

    @Test
    void newSecretValueIsStored() {
        ExternalServicesProperties property = new ExternalServicesProperties().setValue(S3_SECRET);

        Map<String, Object> changes = property.update(command(ExternalServicesConstants.S3_SECRET_KEY, "new-secret"),
                ExternalServicesConstants.S3_SECRET_KEY);

        assertEquals("new-secret", changes.get("value"));
        assertEquals("new-secret", property.getValue());
    }

    @Test
    void nonSecretPropertyAcceptsAnyValue() {
        ExternalServicesProperties property = new ExternalServicesProperties().setValue("acme-documents");

        Map<String, Object> changes = property.update(
                command(ExternalServicesConstants.S3_BUCKET_NAME, ExternalServicesConstants.MASKED_SECRET_VALUE),
                ExternalServicesConstants.S3_BUCKET_NAME);

        assertEquals(ExternalServicesConstants.MASKED_SECRET_VALUE, changes.get("value"));
        assertEquals(ExternalServicesConstants.MASKED_SECRET_VALUE, property.getValue());
    }

    private static JsonCommand command(String name, String value) {
        FromJsonHelper fromJsonHelper = new FromJsonHelper();
        String json = "{\"" + name + "\": \"" + value + "\"}";
        return new JsonCommand(1L, fromJsonHelper.parse(json), fromJsonHelper);
    }
}
