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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.campaigns.email.EmailApiConstants;
import org.apache.fineract.infrastructure.campaigns.email.data.EmailConfigurationValidator;
import org.apache.fineract.infrastructure.campaigns.email.domain.EmailConfiguration;
import org.apache.fineract.infrastructure.campaigns.email.domain.EmailConfigurationRepository;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailConfigurationWritePlatformServiceImplTest {

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private EmailConfigurationRepository repository;

    @Mock
    private EmailConfigurationValidator validator;

    @Mock
    private JsonCommand command;

    private EmailConfiguration password;
    private EmailConfiguration username;
    private EmailConfigurationWritePlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        password = new EmailConfiguration().setName(EmailApiConstants.SMTP_PASSWORD).setValue("old-secret");
        username = new EmailConfiguration().setName(EmailApiConstants.SMTP_USERNAME).setValue("old@acme.example");
        when(repository.findAll()).thenReturn(List.of(password, username));
        when(validator.isValidEmail(anyString())).thenReturn(true);
        when(command.json()).thenReturn("{}");
        when(command.stringValueOfParameterNamed(EmailApiConstants.SMTP_USERNAME)).thenReturn("new@acme.example");
        service = new EmailConfigurationWritePlatformServiceImpl(context, repository, validator);
    }

    @Test
    void newPasswordIsStoredButMaskedInChanges() {
        when(command.stringValueOfParameterNamed(EmailApiConstants.SMTP_PASSWORD)).thenReturn("new-secret");

        CommandProcessingResult result = service.update(command);

        assertEquals("new-secret", password.getValue());
        Map<String, Object> changes = result.getChanges();
        assertEquals(EmailApiConstants.MASKED_SECRET_VALUE, changes.get(EmailApiConstants.SMTP_PASSWORD));
        assertEquals("new@acme.example", changes.get(EmailApiConstants.SMTP_USERNAME));
    }

    @Test
    void echoedMaskKeepsStoredPassword() {
        when(command.stringValueOfParameterNamed(EmailApiConstants.SMTP_PASSWORD)).thenReturn(EmailApiConstants.MASKED_SECRET_VALUE);

        CommandProcessingResult result = service.update(command);

        assertEquals("old-secret", password.getValue());
        assertEquals("new@acme.example", username.getValue());
        assertFalse(result.getChanges().containsKey(EmailApiConstants.SMTP_PASSWORD));
    }
}
