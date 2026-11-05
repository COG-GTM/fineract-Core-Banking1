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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.campaigns.email.EmailApiConstants;
import org.apache.fineract.infrastructure.campaigns.email.data.EmailConfigurationValidator;
import org.apache.fineract.infrastructure.campaigns.email.domain.EmailConfiguration;
import org.apache.fineract.infrastructure.campaigns.email.domain.EmailConfigurationRepository;
import org.apache.fineract.infrastructure.campaigns.email.exception.EmailConfigurationSMTPUsernameNotValid;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailConfigurationWritePlatformServiceImpl implements EmailConfigurationWritePlatformService {

    private final PlatformSecurityContext context;
    private final EmailConfigurationRepository repository;
    private final EmailConfigurationValidator emailConfigurationValidator;

    @Override
    public CommandProcessingResult update(final JsonCommand command) {
        // TODO: leaving function call for backward compatibility... but security configuration should be done somewhere
        // else
        this.context.authenticatedUser();

        this.emailConfigurationValidator.validateUpdateConfiguration(command.json());
        final String smtpUsername = command.stringValueOfParameterNamed("SMTP_USERNAME");

        if (!this.emailConfigurationValidator.isValidEmail(smtpUsername)) {
            throw new EmailConfigurationSMTPUsernameNotValid(smtpUsername);
        }

        final Map<String, Object> changes = new HashMap<>(4);

        Collection<EmailConfiguration> configurations = this.repository.findAll();
        /**
         * Default SMTP configuration added to flyway
         */
        for (EmailConfiguration config : configurations) {
            if (config.getName() != null) {
                String value = command.stringValueOfParameterNamed(config.getName());
                final boolean secret = EmailApiConstants.isSecretConfiguration(config.getName());
                if (secret && EmailApiConstants.MASKED_SECRET_VALUE.equals(value)) {
                    // client echoed back the masked value from GET; keep the stored secret
                    continue;
                }
                config.setValue(value);
                changes.put(config.getName(), secret ? EmailApiConstants.MASKED_SECRET_VALUE : value);
                this.repository.saveAndFlush(config);
            }
        }

        return new CommandProcessingResultBuilder() //
                .with(changes).build();

    }

}
