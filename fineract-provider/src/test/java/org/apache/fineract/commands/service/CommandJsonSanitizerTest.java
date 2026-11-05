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
package org.apache.fineract.commands.service;

import static org.apache.fineract.commands.service.CommandSourceService.COMMAND_MASK_VALUE;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CommandJsonSanitizerTest {

    @Test
    public void testMaskSecretsMasksExternalServiceAndUserSecrets() {
        String json = "{\"username\":\"mailer\",\"password\":\"smtp-pass\",\"s3_secret_key\":\"s3-secret\",\"s3_access_key\":\"AKIA\","
                + "\"tenant_app_key\":\"sms-key\",\"server_key\":\"fcm-key\",\"repeatPassword\":\"smtp-pass\",\"host\":\"smtp.acme\"}";

        JsonObject masked = JsonParser.parseString(CommandJsonSanitizer.maskSecrets(json)).getAsJsonObject();

        Assertions.assertEquals(COMMAND_MASK_VALUE, masked.get("password").getAsString());
        Assertions.assertEquals(COMMAND_MASK_VALUE, masked.get("s3_secret_key").getAsString());
        Assertions.assertEquals(COMMAND_MASK_VALUE, masked.get("tenant_app_key").getAsString());
        Assertions.assertEquals(COMMAND_MASK_VALUE, masked.get("server_key").getAsString());
        Assertions.assertEquals(COMMAND_MASK_VALUE, masked.get("repeatPassword").getAsString());
        Assertions.assertEquals("mailer", masked.get("username").getAsString());
        Assertions.assertEquals("AKIA", masked.get("s3_access_key").getAsString());
        Assertions.assertEquals("smtp.acme", masked.get("host").getAsString());
    }

    @Test
    public void testMaskSecretsLeavesOtherPayloadsUntouched() {
        String json = "{\"officeId\":1,\"name\":\"Branch\"}";
        Assertions.assertSame(json, CommandJsonSanitizer.maskSecrets(json));
        Assertions.assertNull(CommandJsonSanitizer.maskSecrets(null));
        Assertions.assertEquals("", CommandJsonSanitizer.maskSecrets(""));
        Assertions.assertEquals("[1,2]", CommandJsonSanitizer.maskSecrets("[1,2]"));
        Assertions.assertEquals("not json {", CommandJsonSanitizer.maskSecrets("not json {"));
    }

    @Test
    public void testUpdateExternalServicePropertiesSanitizesSecretKeys() {
        CommandWrapper wrapper = new CommandWrapperBuilder().updateExternalServiceProperties("SMTP").withJson("{}").build();

        Assertions.assertEquals(CommandJsonSanitizer.EXTERNAL_SERVICE_SECRET_KEYS, wrapper.getSanitizeJsonKeys());
    }
}
