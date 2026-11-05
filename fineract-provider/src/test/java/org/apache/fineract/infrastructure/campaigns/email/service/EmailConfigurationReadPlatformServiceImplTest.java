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
package org.apache.fineract.infrastructure.campaigns.email.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.fineract.infrastructure.campaigns.email.EmailApiConstants;
import org.apache.fineract.infrastructure.campaigns.email.data.EmailConfigurationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class EmailConfigurationReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ResultSet resultSet;

    private RowMapper<EmailConfigurationData> rowMapper;

    @BeforeEach
    void setUp() {
        new EmailConfigurationReadPlatformServiceImpl(jdbcTemplate).retrieveAll();
        ArgumentCaptor<RowMapper<EmailConfigurationData>> captor = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbcTemplate).query(anyString(), captor.capture());
        rowMapper = captor.getValue();
    }

    private EmailConfigurationData mapRow(String name, String value) throws SQLException {
        when(resultSet.getString("name")).thenReturn(name);
        when(resultSet.getString("value")).thenReturn(value);
        return rowMapper.mapRow(resultSet, 0);
    }

    @Test
    void smtpPasswordIsMasked() throws SQLException {
        EmailConfigurationData data = mapRow(EmailApiConstants.SMTP_PASSWORD, "S3cr3t-Passw0rd!");

        assertEquals(EmailApiConstants.SMTP_PASSWORD, data.getName());
        assertEquals(EmailApiConstants.MASKED_SECRET_VALUE, data.getValue());
    }

    @Test
    void unsetSmtpPasswordStaysUnset() throws SQLException {
        assertNull(mapRow(EmailApiConstants.SMTP_PASSWORD, null).getValue());
    }

    @Test
    void nonSecretValuesAreReturnedUnchanged() throws SQLException {
        assertEquals("smtp.acme.example", mapRow(EmailApiConstants.SMTP_SERVER, "smtp.acme.example").getValue());
        assertEquals("ops@acme.example", mapRow(EmailApiConstants.SMTP_USERNAME, "ops@acme.example").getValue());
    }
}
