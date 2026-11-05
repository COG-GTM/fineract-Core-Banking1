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
package org.apache.fineract.integrationtests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.ArrayList;
import java.util.HashMap;
import org.apache.fineract.integrationtests.common.ExternalServicesConfigurationHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalServicesConfigurationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalServicesConfigurationTest.class);
    private static final String MASKED_SECRET_VALUE = "********";
    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;
    private ExternalServicesConfigurationHelper externalServicesConfigurationHelper;
    private ResponseSpecification httpStatusForidden;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.httpStatusForidden = new ResponseSpecBuilder().expectStatusCode(403).build();

    }

    @Test
    public void testExternalServicesConfiguration() {
        this.externalServicesConfigurationHelper = new ExternalServicesConfigurationHelper(this.requestSpec, this.responseSpec);

        // Checking for S3
        String configName = "s3_bucket_name";
        ArrayList<HashMap> externalServicesConfig = ExternalServicesConfigurationHelper
                .getExternalServicesConfigurationByServiceName(requestSpec, responseSpec, "S3");
        Assertions.assertNotNull(externalServicesConfig);
        for (Integer configIndex = 0; configIndex < externalServicesConfig.size(); configIndex++) {
            String name = (String) externalServicesConfig.get(configIndex).get("name");
            String value = null;
            if (name.equals(configName)) {
                value = (String) externalServicesConfig.get(configIndex).get("value");
                if (value == null) {
                    value = "testnull";
                }
                String newValue = "test";
                LOG.info("{} : {}", name, value);
                HashMap arrayListValue = ExternalServicesConfigurationHelper.updateValueForExternaServicesConfiguration(requestSpec,
                        responseSpec, "S3", name, newValue);
                Assertions.assertNotNull(arrayListValue.get("value"));
                Assertions.assertEquals(arrayListValue.get("value"), newValue);
                HashMap arrayListValue1 = ExternalServicesConfigurationHelper.updateValueForExternaServicesConfiguration(requestSpec,
                        responseSpec, "S3", name, value);
                Assertions.assertNotNull(arrayListValue1.get("value"));
                Assertions.assertEquals(arrayListValue1.get("value"), value);
            }

        }

        // Checking for SMTP:
        configName = "username";
        externalServicesConfig = ExternalServicesConfigurationHelper.getExternalServicesConfigurationByServiceName(requestSpec,
                responseSpec, "SMTP");
        Assertions.assertNotNull(externalServicesConfig);

        for (Integer configIndex = 0; configIndex < externalServicesConfig.size(); configIndex++) {
            String name = (String) externalServicesConfig.get(configIndex).get("name");
            String value = null;
            if (name.equals(configName)) {
                value = (String) externalServicesConfig.get(configIndex).get("value");
                if (value == null) {
                    value = "testnull";
                }
                String newValue = "test";
                LOG.info("{} : {}", name, value);
                HashMap arrayListValue = ExternalServicesConfigurationHelper.updateValueForExternaServicesConfiguration(requestSpec,
                        responseSpec, "SMTP", name, newValue);
                Assertions.assertNotNull(arrayListValue.get("value"));
                Assertions.assertEquals(arrayListValue.get("value"), newValue);
                HashMap arrayListValue1 = ExternalServicesConfigurationHelper.updateValueForExternaServicesConfiguration(requestSpec,
                        responseSpec, "SMTP", name, value);
                Assertions.assertNotNull(arrayListValue1.get("value"));
                Assertions.assertEquals(arrayListValue1.get("value"), value);
            }

        }

        // Checking for Notifications:
        configName = "server_key";
        externalServicesConfig = ExternalServicesConfigurationHelper.getExternalServicesConfigurationByServiceName(requestSpec,
                responseSpec, "NOTIFICATION");
        Assertions.assertNotNull(externalServicesConfig);

        for (Integer configIndex = 0; configIndex < externalServicesConfig.size(); configIndex++) {
            String name = (String) externalServicesConfig.get(configIndex).get("name");
            String value = null;
            if (name.equals(configName)) {
                value = (String) externalServicesConfig.get(configIndex).get("value");
                if (value == null) {
                    value = "testnull";
                }
                LOG.info("{} : {}", name, value);
                assertTrue(hasMoreThanThreeStars(value));
            }

        }
    }

    @Test
    public void testExternalServiceSecretsAreMasked() {
        assertSecretIsMasked("S3", "s3_secret_key", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        assertSecretIsMasked("S3", "s3_access_key", "AKIAIOSFODNN7EXAMPLE");
        assertSecretIsMasked("SMS", "tenant_app_key", "tenant-app-key-1234567890");
    }

    private void assertSecretIsMasked(String serviceName, String propertyName, String secret) {
        HashMap changes = ExternalServicesConfigurationHelper.updateValueForExternaServicesConfiguration(requestSpec, responseSpec,
                serviceName, propertyName, secret);
        Assertions.assertEquals(secret, changes.get("value"));

        String returnedValue = findValue(serviceName, propertyName);
        Assertions.assertEquals(MASKED_SECRET_VALUE, returnedValue);

        // Sending back the masked placeholder must not overwrite the stored secret
        HashMap noChanges = ExternalServicesConfigurationHelper.updateValueForExternaServicesConfiguration(requestSpec, responseSpec,
                serviceName, propertyName, MASKED_SECRET_VALUE);
        Assertions.assertTrue(noChanges == null || noChanges.isEmpty());
        HashMap stillSame = ExternalServicesConfigurationHelper.updateValueForExternaServicesConfiguration(requestSpec, responseSpec,
                serviceName, propertyName, secret);
        Assertions.assertTrue(stillSame == null || stillSame.isEmpty());

        // Clear the secret again
        ExternalServicesConfigurationHelper.updateValueForExternaServicesConfiguration(requestSpec, responseSpec, serviceName,
                propertyName, "");
        Assertions.assertNull(findValue(serviceName, propertyName));
    }

    private String findValue(String serviceName, String propertyName) {
        ArrayList<HashMap> config = ExternalServicesConfigurationHelper.getExternalServicesConfigurationByServiceName(requestSpec,
                responseSpec, serviceName);
        for (HashMap property : config) {
            if (propertyName.equals(property.get("name"))) {
                return (String) property.get("value");
            }
        }
        Assertions.fail(propertyName + " not found for " + serviceName);
        return null;
    }

    private boolean hasMoreThanThreeStars(String input) {
        return input != null && input.matches("(.*\\*.*){4,}");
    }
}
