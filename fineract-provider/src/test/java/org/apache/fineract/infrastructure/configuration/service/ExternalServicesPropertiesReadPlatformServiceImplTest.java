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
package org.apache.fineract.infrastructure.configuration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.configuration.data.ExternalServicesPropertiesData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class ExternalServicesPropertiesReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ExternalServicesPropertiesReadPlatformServiceImpl readPlatformService;

    @Test
    void retrieveOneMasksS3Credentials() throws SQLException {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put(ExternalServicesConstants.S3_BUCKET_NAME, "acme-documents");
        rows.put(ExternalServicesConstants.S3_ACCESS_KEY, "AKIAIOSFODNN7EXAMPLE");
        rows.put(ExternalServicesConstants.S3_SECRET_KEY, "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");

        Map<String, String> result = retrieve("S3", rows);

        assertEquals("acme-documents", result.get(ExternalServicesConstants.S3_BUCKET_NAME));
        assertEquals(ExternalServicesConstants.MASKED_SECRET_VALUE, result.get(ExternalServicesConstants.S3_ACCESS_KEY));
        assertEquals(ExternalServicesConstants.MASKED_SECRET_VALUE, result.get(ExternalServicesConstants.S3_SECRET_KEY));
    }

    @Test
    void retrieveOneMasksSmsTenantAppKey() throws SQLException {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put(ExternalServicesConstants.SMS_HOST, "sms.example.com");
        rows.put(ExternalServicesConstants.SMS_PORT, "9191");
        rows.put(ExternalServicesConstants.SMS_END_POINT, "/");
        rows.put(ExternalServicesConstants.SMS_TENANT_APP_KEY, "tenant-app-key-123456");

        Map<String, String> result = retrieve("SMS", rows);

        assertEquals("sms.example.com", result.get(ExternalServicesConstants.SMS_HOST));
        assertEquals("9191", result.get(ExternalServicesConstants.SMS_PORT));
        assertEquals("/", result.get(ExternalServicesConstants.SMS_END_POINT));
        assertEquals(ExternalServicesConstants.MASKED_SECRET_VALUE, result.get(ExternalServicesConstants.SMS_TENANT_APP_KEY));
    }

    @Test
    void retrieveOneMasksSmtpPasswordAndNotificationServerKey() throws SQLException {
        Map<String, String> smtpRows = new LinkedHashMap<>();
        smtpRows.put(ExternalServicesConstants.SMTP_USERNAME, "mailer@example.com");
        smtpRows.put(ExternalServicesConstants.SMTP_PASSWORD, "smtp-password");
        smtpRows.put(ExternalServicesConstants.SMTP_HOST, "smtp.example.com");

        Map<String, String> smtp = retrieve("SMTP", smtpRows);
        assertEquals("mailer@example.com", smtp.get(ExternalServicesConstants.SMTP_USERNAME));
        assertEquals("smtp.example.com", smtp.get(ExternalServicesConstants.SMTP_HOST));
        assertEquals(ExternalServicesConstants.MASKED_SECRET_VALUE, smtp.get(ExternalServicesConstants.SMTP_PASSWORD));

        Map<String, String> notificationRows = new LinkedHashMap<>();
        notificationRows.put(ExternalServicesConstants.NOTIFICATION_SERVER_KEY, "AAAA-server-key");
        notificationRows.put(ExternalServicesConstants.NOTIFICATION_FCM_END_POINT, "https://fcm.googleapis.com/fcm/send");

        Map<String, String> notification = retrieve("NOTIFICATION", notificationRows);
        assertEquals(ExternalServicesConstants.MASKED_SECRET_VALUE, notification.get(ExternalServicesConstants.NOTIFICATION_SERVER_KEY));
        assertEquals("https://fcm.googleapis.com/fcm/send", notification.get(ExternalServicesConstants.NOTIFICATION_FCM_END_POINT));
    }

    @Test
    void retrieveOneMasksUnknownPropertiesAndKeepsUnsetSecretsNull() throws SQLException {
        Map<String, String> rows = new LinkedHashMap<>();
        rows.put("some_future_api_token", "token-value");
        rows.put(ExternalServicesConstants.S3_SECRET_KEY, null);

        Map<String, String> result = retrieve("S3", rows);

        assertEquals(ExternalServicesConstants.MASKED_SECRET_VALUE, result.get("some_future_api_token"));
        assertNull(result.get(ExternalServicesConstants.S3_SECRET_KEY));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> retrieve(String serviceName, Map<String, String> rows) throws SQLException {
        doAnswer(invocation -> {
            RowMapper<ExternalServicesPropertiesData> mapper = invocation.getArgument(1);
            List<ExternalServicesPropertiesData> mapped = new ArrayList<>();
            int rowNum = 0;
            for (Map.Entry<String, String> row : rows.entrySet()) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString(eq("name"))).thenReturn(row.getKey());
                when(rs.getString(eq("value"))).thenReturn(row.getValue());
                mapped.add(mapper.mapRow(rs, rowNum++));
            }
            return mapped;
        }).when(jdbcTemplate).query(anyString(), ArgumentMatchers.<RowMapper<ExternalServicesPropertiesData>>any());

        Collection<ExternalServicesPropertiesData> data = readPlatformService.retrieveOne(serviceName);
        Map<String, String> result = new LinkedHashMap<>();
        data.forEach(d -> result.put(d.getName(), d.getValue()));
        return result;
    }
}
