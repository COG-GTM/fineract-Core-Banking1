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
package org.apache.fineract.infrastructure.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.apache.fineract.infrastructure.configuration.data.SMTPCredentialsData;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class GmailBackedPlatformEmailServiceTest {

    private static Properties propertiesFor(String host, String port) {
        SMTPCredentialsData credentials = new SMTPCredentialsData().setHost(host).setPort(port).setUsername("user").setPassword("secret");
        JavaMailSenderImpl mailSender = GmailBackedPlatformEmailService.createMailSender(credentials);
        assertThat(mailSender.getHost()).isEqualTo(host);
        assertThat(mailSender.getPort()).isEqualTo(Integer.parseInt(port));
        return mailSender.getJavaMailProperties();
    }

    @Test
    void remoteSubmissionPortRequiresStartTls() {
        Properties props = propertiesFor("smtp.example.com", "587");

        assertThat(props.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
        assertThat(props).doesNotContainKeys("mail.smtp.socketFactory.class", "mail.smtp.socketFactory.fallback", "mail.smtp.ssl.enable",
                "mail.debug");
    }

    @Test
    void smtpsPortUsesImplicitTlsWithoutFallback() {
        Properties props = propertiesFor("smtp.example.com", "465");

        assertThat(props.getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
        assertThat(props).doesNotContainKeys("mail.smtp.socketFactory.class", "mail.smtp.socketFactory.fallback");
    }

    @Test
    void remoteIpLiteralRequiresStartTls() {
        assertThat(propertiesFor("10.0.0.25", "25").getProperty("mail.smtp.starttls.required")).isEqualTo("true");
    }

    @Test
    void loopbackRelayDoesNotRequireStartTls() {
        assertThat(propertiesFor("localhost", "3025").getProperty("mail.smtp.starttls.required")).isEqualTo("false");
        assertThat(propertiesFor("127.0.0.1", "25").getProperty("mail.smtp.starttls.required")).isEqualTo("false");
        assertThat(propertiesFor("::1", "25").getProperty("mail.smtp.starttls.required")).isEqualTo("false");
    }

    @Test
    void hostnameThatOnlyLooksLocalIsNotTreatedAsLoopback() {
        assertThat(propertiesFor("localhost.attacker.example", "587").getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(propertiesFor("127.0.0.1.nip.io", "587").getProperty("mail.smtp.starttls.required")).isEqualTo("true");
    }
}
