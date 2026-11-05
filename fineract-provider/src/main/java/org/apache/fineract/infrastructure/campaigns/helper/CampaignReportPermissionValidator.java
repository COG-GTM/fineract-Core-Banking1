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
package org.apache.fineract.infrastructure.campaigns.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures the report named in a campaign's paramValue is an SMS/Email campaign report and that the user creating,
 * updating or previewing the campaign may run it, the same way /runreports checks READ_&lt;report&gt;.
 */
@Component
@RequiredArgsConstructor
public class CampaignReportPermissionValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> CAMPAIGN_REPORT_TYPES = Set.of("sms", "email");

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    public void validateReportPermission(final String paramValueJson) {
        if (StringUtils.isBlank(paramValueJson)) {
            return;
        }
        final JsonNode reportNameNode;
        try {
            final JsonNode paramValue = OBJECT_MAPPER.readTree(paramValueJson);
            if (paramValue == null || !paramValue.isObject()) {
                throw invalidParamValue(null);
            }
            reportNameNode = paramValue.get("reportName");
        } catch (final JsonProcessingException e) {
            throw invalidParamValue(e);
        }
        if (reportNameNode == null || reportNameNode.isNull()) {
            return;
        }
        final String reportName = reportNameNode.asText();
        if (this.context.authenticatedUser().hasNotPermissionForReport(reportName)) {
            throw new NoAuthorizationException("Not authorised to run report: " + reportName);
        }
        validateCampaignReportType(reportName);
    }

    private void validateCampaignReportType(final String reportName) {
        final List<String> reportTypes = this.jdbcTemplate.queryForList("SELECT report_type FROM stretchy_report WHERE report_name = ?",
                String.class, reportName);
        final boolean allowed = !reportTypes.isEmpty() && reportTypes.stream()
                .allMatch(reportType -> reportType != null && CAMPAIGN_REPORT_TYPES.contains(reportType.toLowerCase(Locale.ROOT)));
        if (!allowed) {
            throw new GeneralPlatformDomainRuleException("error.msg.campaign.report.type.not.allowed",
                    "Only SMS or Email reports can be used by campaigns", reportName);
        }
    }

    private static GeneralPlatformDomainRuleException invalidParamValue(final Throwable cause) {
        return new GeneralPlatformDomainRuleException("error.msg.campaign.paramValue.invalid", "paramValue must be a JSON object", cause);
    }
}
