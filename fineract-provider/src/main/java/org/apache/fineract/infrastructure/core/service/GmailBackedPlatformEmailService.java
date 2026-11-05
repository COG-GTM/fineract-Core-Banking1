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

import com.google.common.net.InetAddresses;
import java.util.Properties;
import org.apache.fineract.infrastructure.configuration.data.SMTPCredentialsData;
import org.apache.fineract.infrastructure.configuration.service.ExternalServicesPropertiesReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
public class GmailBackedPlatformEmailService implements PlatformEmailService {

    private static final int SMTPS_PORT = 465;

    private final ExternalServicesPropertiesReadPlatformService externalServicesReadPlatformService;

    @Autowired
    public GmailBackedPlatformEmailService(final ExternalServicesPropertiesReadPlatformService externalServicesReadPlatformService) {
        this.externalServicesReadPlatformService = externalServicesReadPlatformService;
    }

    @Override
    public void sendToUserAccount(String organisationName, String contactName, String address, String username, String unencodedPassword) {

        final String subject = "Welcome " + contactName + " to " + organisationName;
        final String body = "You are receiving this email as your email account: " + address
                + " has being used to create a user account for an organisation named [" + organisationName + "] on Mifos.\n"
                + "You can login using the following credentials:\nusername: " + username + "\n" + "password: " + unencodedPassword + "\n"
                + "You must change this password upon first log in using Uppercase, Lowercase, number and character.\n"
                + "Thank you and welcome to the organisation.";

        final EmailDetail emailDetail = new EmailDetail(subject, body, address, contactName);
        sendDefinedEmail(emailDetail);

    }

    @Override
    public void sendDefinedEmail(EmailDetail emailDetails) {
        final SMTPCredentialsData smtpCredentialsData = this.externalServicesReadPlatformService.getSMTPCredentials();

        final JavaMailSenderImpl mailSender = createMailSender(smtpCredentialsData);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(smtpCredentialsData.getFromEmail()); // same email address used for the authentication
            message.setTo(emailDetails.getAddress());
            message.setSubject(emailDetails.getSubject());
            message.setText(emailDetails.getBody());
            mailSender.send(message);

        } catch (Exception e) {
            throw new PlatformEmailSendException(e);
        }
    }

    static JavaMailSenderImpl createMailSender(final SMTPCredentialsData smtpCredentialsData) {
        final JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        final int port = Integer.parseInt(smtpCredentialsData.getPort());
        mailSender.setHost(smtpCredentialsData.getHost());
        mailSender.setPort(port);
        mailSender.setUsername(smtpCredentialsData.getUsername());
        mailSender.setPassword(smtpCredentialsData.getPassword());

        final Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.checkserveridentity", "true");

        if (port == SMTPS_PORT) {
            // Implicit TLS (SMTPS): the connection is TLS from the first byte, no plain-text fallback.
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            // STARTTLS must succeed before credentials or message content are sent, otherwise an on-path
            // attacker could strip it. Loopback relays have no network path to intercept.
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", String.valueOf(!isLoopbackHost(smtpCredentialsData.getHost())));
        }
        return mailSender;
    }

    private static boolean isLoopbackHost(final String host) {
        if (host == null) {
            return false;
        }
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        // Only IP literals are checked; resolving a hostname here could differ from what the mail client connects to.
        return InetAddresses.isInetAddress(host) && InetAddresses.forString(host).isLoopbackAddress();
    }
}
