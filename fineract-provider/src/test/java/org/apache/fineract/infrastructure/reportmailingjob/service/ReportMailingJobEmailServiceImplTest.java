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
package org.apache.fineract.infrastructure.reportmailingjob.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Properties;
import org.apache.fineract.infrastructure.reportmailingjob.ReportMailingJobConstants;
import org.apache.fineract.infrastructure.reportmailingjob.data.ReportMailingJobConfigurationData;
import org.junit.jupiter.api.Test;

class ReportMailingJobEmailServiceImplTest {

    private final ReportMailingJobEmailServiceImpl service = new ReportMailingJobEmailServiceImpl(
            mock(ReportMailingJobConfigurationReadPlatformService.class));

    @Test
    void starttlsIsRequiredAndCertificatesAreNotBlindlyTrusted() {
        Properties properties = service.getJavaMailProperties(configuration("smtp.example.com", "587"));

        assertEquals("true", properties.getProperty("mail.smtp.auth"));
        assertEquals("true", properties.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", properties.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", properties.getProperty("mail.smtp.ssl.checkserveridentity"));
        assertNull(properties.getProperty("mail.smtp.ssl.trust"));
        assertFalse(properties.containsKey("mail.smtp.ssl.enable"));
    }

    @Test
    void smtpsPortUsesImplicitTls() {
        Properties properties = service.getJavaMailProperties(configuration("smtp.example.com", "465"));

        assertEquals("true", properties.getProperty("mail.smtp.ssl.enable"));
        assertEquals("true", properties.getProperty("mail.smtp.ssl.checkserveridentity"));
        assertNull(properties.getProperty("mail.smtp.ssl.trust"));
        assertFalse(properties.containsKey("mail.smtp.starttls.enable"));
    }

    private static List<ReportMailingJobConfigurationData> configuration(String host, String port) {
        return List.of(ReportMailingJobConfigurationData.newInstance(1, ReportMailingJobConstants.GMAIL_SMTP_SERVER, host),
                ReportMailingJobConfigurationData.newInstance(2, ReportMailingJobConstants.GMAIL_SMTP_PORT, port));
    }
}
