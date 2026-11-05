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
package org.apache.fineract.infrastructure.campaigns.jobs.executereportmailingjobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.dataqueries.domain.Report;
import org.apache.fineract.infrastructure.dataqueries.domain.ReportParameter;
import org.apache.fineract.infrastructure.dataqueries.domain.ReportParameterUsage;
import org.apache.fineract.infrastructure.dataqueries.service.ReadReportingService;
import org.apache.fineract.infrastructure.report.provider.ReportingProcessServiceProvider;
import org.apache.fineract.infrastructure.report.service.ReportingProcessService;
import org.apache.fineract.infrastructure.reportmailingjob.domain.ReportMailingJob;
import org.apache.fineract.infrastructure.reportmailingjob.domain.ReportMailingJobRepository;
import org.apache.fineract.infrastructure.reportmailingjob.domain.ReportMailingJobRunHistoryRepository;
import org.apache.fineract.infrastructure.reportmailingjob.service.ReportMailingJobEmailService;
import org.apache.fineract.infrastructure.reportmailingjob.validation.ReportMailingJobValidator;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class ExecuteReportMailingJobsTaskletTest {

    private static final String REPORT_NAME = "Client Listing";

    private final ReportMailingJobRepository reportMailingJobRepository = mock(ReportMailingJobRepository.class);
    private final ReadReportingService readReportingService = mock(ReadReportingService.class);
    private final ReportingProcessServiceProvider reportingProcessServiceProvider = mock(ReportingProcessServiceProvider.class);
    private final ReportingProcessService reportingProcessService = mock(ReportingProcessService.class);
    private final ReportMailingJobEmailService emailService = mock(ReportMailingJobEmailService.class);
    private final FineractProperties fineractProperties = mock(FineractProperties.class, RETURNS_DEEP_STUBS);
    private final AtomicReference<Object> principalDuringReport = new AtomicReference<>();
    private final AppUser systemUser = mock(AppUser.class);
    private Authentication systemAuthentication;
    private ExecuteReportMailingJobsTasklet tasklet;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.now(ZoneId.systemDefault()))));
        systemAuthentication = new UsernamePasswordAuthenticationToken(systemUser, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(systemAuthentication);

        when(readReportingService.getReportType(anyString(), anyBoolean(), anyBoolean())).thenReturn("Table");
        when(reportingProcessServiceProvider.findReportingProcessService("Table")).thenReturn(reportingProcessService);
        when(reportingProcessService.processRequest(anyString(), any())).thenAnswer(invocation -> {
            principalDuringReport.set(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            return Response.ok(new ByteArrayOutputStream()).build();
        });
        when(fineractProperties.getContent().getFilesystem().getRootFolder()).thenReturn(System.getProperty("java.io.tmpdir"));

        tasklet = new ExecuteReportMailingJobsTasklet(reportMailingJobRepository, new ReportMailingJobValidator(new FromJsonHelper()),
                readReportingService, reportingProcessServiceProvider, emailService, mock(ReportMailingJobRunHistoryRepository.class),
                fineractProperties);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        ThreadLocalContextUtil.reset();
    }

    @Test
    void runsReportAsRunAsUserAndRestoresSystemUser() throws Exception {
        final AppUser runAsUser = activeUser(false);
        final ReportMailingJob job = dueJob(runAsUser, "{\"R_officeId\":\"2\"}");

        tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

        assertEquals(runAsUser, principalDuringReport.get());
        assertEquals(systemAuthentication, SecurityContextHolder.getContext().getAuthentication());
        assertEquals(1, job.getNumberOfRuns());
    }

    @Test
    void skipsReportWhenRunAsUserLacksReportPermission() throws Exception {
        final ReportMailingJob job = dueJob(activeUser(true), "{\"R_officeId\":\"2\"}");

        tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

        verify(reportingProcessService, never()).processRequest(anyString(), any());
        assertNull(principalDuringReport.get());
        assertTrue(job.getPreviousRunErrorLog().contains("not authorised to run report: " + REPORT_NAME));
    }

    @Test
    void skipsReportWhenParamMapHasUndeclaredParameters() throws Exception {
        final ReportMailingJob job = dueJob(activeUser(false), "{\"R_officeId\":\"2\",\"R_userId\":\"1\"}");

        tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));

        verify(reportingProcessService, never()).processRequest(anyString(), any());
        assertTrue(job.getPreviousRunErrorLog().contains("R_userId"));
    }

    private AppUser activeUser(final boolean lacksReportPermission) {
        final AppUser user = mock(AppUser.class);
        when(user.isEnabled()).thenReturn(true);
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.hasNotPermissionForReport(REPORT_NAME)).thenReturn(lacksReportPermission);
        return user;
    }

    private ReportMailingJob dueJob(final AppUser runAsUser, final String paramMap) {
        final ReportParameter parameter = mock(ReportParameter.class);
        when(parameter.getParameterVariable()).thenReturn("officeId");
        final ReportParameterUsage usage = mock(ReportParameterUsage.class);
        when(usage.getParameter()).thenReturn(parameter);
        final Report report = mock(Report.class);
        when(report.getReportName()).thenReturn(REPORT_NAME);
        when(report.getReportParameterUsages()).thenReturn(Set.of(usage));

        final ReportMailingJob job = new ReportMailingJob().setName("daily").setStretchyReport(report).setStretchyReportParamMap(paramMap)
                .setEmailAttachmentFileFormat("csv").setEmailRecipients("ops@example.com").setActive(true).setNumberOfRuns(0)
                .setRunAsUser(runAsUser).setNextRunDateTime(DateUtils.getLocalDateTimeOfTenant().minusMinutes(5));
        when(reportMailingJobRepository.findByIsActiveTrueAndIsDeletedFalse()).thenReturn(List.of(job));
        return job;
    }
}
