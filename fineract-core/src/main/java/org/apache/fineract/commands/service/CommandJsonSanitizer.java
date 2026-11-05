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
import static org.apache.fineract.useradministration.service.AppUserConstants.PASSWORD;
import static org.apache.fineract.useradministration.service.AppUserConstants.REPEAT_PASSWORD;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.Set;

public final class CommandJsonSanitizer {

    public static final String S3_SECRET_KEY = "s3_secret_key";
    public static final String SMS_TENANT_APP_KEY = "tenant_app_key";
    public static final String NOTIFICATION_SERVER_KEY = "server_key";

    public static final Set<String> EXTERNAL_SERVICE_SECRET_KEYS = Set.of(PASSWORD, S3_SECRET_KEY, SMS_TENANT_APP_KEY,
            NOTIFICATION_SERVER_KEY);

    public static final Set<String> AUDIT_SECRET_KEYS = buildAuditSecretKeys();

    private CommandJsonSanitizer() {}

    public static String maskSecrets(final String commandAsJson) {
        return mask(commandAsJson, AUDIT_SECRET_KEYS);
    }

    public static String mask(final String commandAsJson, final Set<String> keys) {
        if (commandAsJson == null || commandAsJson.isBlank() || keys == null || keys.isEmpty()) {
            return commandAsJson;
        }
        final JsonElement parsed;
        try {
            parsed = JsonParser.parseString(commandAsJson);
        } catch (JsonParseException e) {
            return commandAsJson;
        }
        if (!parsed.isJsonObject()) {
            return commandAsJson;
        }
        final JsonObject jsonObject = parsed.getAsJsonObject();
        boolean masked = false;
        for (String key : keys) {
            if (jsonObject.has(key) && !jsonObject.get(key).isJsonNull()) {
                jsonObject.addProperty(key, COMMAND_MASK_VALUE);
                masked = true;
            }
        }
        return masked ? jsonObject.toString() : commandAsJson;
    }

    private static Set<String> buildAuditSecretKeys() {
        final Set<String> keys = new HashSet<>(EXTERNAL_SERVICE_SECRET_KEYS);
        keys.add(REPEAT_PASSWORD);
        return Set.copyOf(keys);
    }
}
