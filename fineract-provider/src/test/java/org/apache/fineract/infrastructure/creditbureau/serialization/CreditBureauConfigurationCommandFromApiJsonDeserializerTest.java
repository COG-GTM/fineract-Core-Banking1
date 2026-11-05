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
package org.apache.fineract.infrastructure.creditbureau.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.creditbureau.service.CreditBureauUrlValidator;
import org.junit.jupiter.api.Test;

class CreditBureauConfigurationCommandFromApiJsonDeserializerTest {

    private final CreditBureauConfigurationCommandFromApiJsonDeserializer underTest =
            new CreditBureauConfigurationCommandFromApiJsonDeserializer(new FromJsonHelper(), new CreditBureauUrlValidator("", false));

    @Test
    void createRejectsMetadataSearchUrl() {
        PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class, () -> underTest
                .validateForCreate("{\"configkey\":\"searchurl\",\"value\":\"http://169.254.169.254/\",\"description\":\"d\"}", 1L));
        assertEquals("validation.msg.CREDITBUREAU_CONFIGURATION.value.url.scheme.not.https",
                exception.getErrors().get(0).getUserMessageGlobalisationCode());
    }

    @Test
    void createAllowsNonUrlKeys() {
        assertDoesNotThrow(() -> underTest
                .validateForCreate("{\"configkey\":\"Username\",\"value\":\"http://169.254.169.254/\",\"description\":\"d\"}", 1L));
    }

    @Test
    void updateValidatesExistingUrlKey() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateForUpdate("{\"value\":\"https://10.0.0.1/token\"}", "tokenurl", "https://93.184.215.14/token"));
        assertDoesNotThrow(() -> underTest.validateForUpdate("{\"value\":\"https://93.184.215.14/token\"}", "tokenurl", ""));
    }

    @Test
    void updateOfDescriptionOnlyDoesNotRevalidateStoredUrl() {
        assertDoesNotThrow(() -> underTest.validateForUpdate("{\"description\":\"new\"}", "searchurl", "https://unresolvable.invalid/"));
    }

    @Test
    void updateValidatesExistingValueWhenKeyIsRenamedToUrlKey() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateForUpdate("{\"configkey\":\"creditReporturl\"}", "Username", "http://127.0.0.1:8080/"));
    }
}
