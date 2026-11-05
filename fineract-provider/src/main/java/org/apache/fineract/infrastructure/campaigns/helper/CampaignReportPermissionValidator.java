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
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

/**
 * Ensures the user creating, updating or previewing a campaign may run the report named in its paramValue, the same
 * way /runreports checks READ_&lt;report&gt;.
 */
@Component
@RequiredArgsConstructor
public class CampaignReportPermissionValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PlatformSecurityContext context;

    public void validateReportPermission(final String paramValueJson) {
        if (StringUtils.isBlank(paramValueJson)) {
            return;
        }
        final JsonNode reportNameNode;
        try {
            final JsonNode paramValue = OBJECT_MAPPER.readTree(paramValueJson);
            if (paramValue == null || !paramValue.isObject()) {
                throw invalidParamValue();
            }
            reportNameNode = paramValue.get("reportName");
        } catch (final JsonProcessingException e) {
            throw invalidParamValue();
        }
        if (reportNameNode == null || reportNameNode.isNull()) {
            return;
        }
        final String reportName = reportNameNode.asText();
        if (this.context.authenticatedUser().hasNotPermissionForReport(reportName)) {
            throw new NoAuthorizationException("Not authorised to run report: " + reportName);
        }
    }

    private static GeneralPlatformDomainRuleException invalidParamValue() {
        return new GeneralPlatformDomainRuleException("error.msg.campaign.paramValue.invalid", "paramValue must be a JSON object");
    }
}
