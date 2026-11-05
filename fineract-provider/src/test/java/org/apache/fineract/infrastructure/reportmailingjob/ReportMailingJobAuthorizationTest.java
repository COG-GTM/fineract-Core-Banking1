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
package org.apache.fineract.infrastructure.reportmailingjob;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.dataqueries.domain.Report;
import org.apache.fineract.infrastructure.dataqueries.domain.ReportParameter;
import org.apache.fineract.infrastructure.dataqueries.domain.ReportParameterUsage;
import org.apache.fineract.infrastructure.dataqueries.domain.ReportRepositoryWrapper;
import org.apache.fineract.infrastructure.reportmailingjob.domain.ReportMailingJob;
import org.apache.fineract.infrastructure.reportmailingjob.domain.ReportMailingJobRepository;
import org.apache.fineract.infrastructure.reportmailingjob.domain.ReportMailingJobRepositoryWrapper;
import org.apache.fineract.infrastructure.reportmailingjob.service.ReportMailingJobWritePlatformServiceImpl;
import org.apache.fineract.infrastructure.reportmailingjob.validation.ReportMailingJobValidator;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportMailingJobAuthorizationTest {

    private static final String REPORT_NAME = "Client Listing";

    private final ReportMailingJobValidator validator = new ReportMailingJobValidator(new FromJsonHelper());
    private ReportRepositoryWrapper reportRepositoryWrapper;
    private ReportMailingJobValidator mockValidator;
    private ReportMailingJobRepositoryWrapper reportMailingJobRepositoryWrapper;
    private ReportMailingJobRepository reportMailingJobRepository;
    private PlatformSecurityContext context;
    private ReportMailingJobWritePlatformServiceImpl service;
    private Report report;
    private AppUser user;

    @BeforeEach
    void setUp() {
        reportRepositoryWrapper = mock(ReportRepositoryWrapper.class);
        mockValidator = mock(ReportMailingJobValidator.class);
        reportMailingJobRepositoryWrapper = mock(ReportMailingJobRepositoryWrapper.class);
        reportMailingJobRepository = mock(ReportMailingJobRepository.class);
        when(reportMailingJobRepositoryWrapper.getReportMailingJobRepository()).thenReturn(reportMailingJobRepository);
        context = mock(PlatformSecurityContext.class);
        service = new ReportMailingJobWritePlatformServiceImpl(reportRepositoryWrapper, mockValidator, reportMailingJobRepositoryWrapper,
                context);

        report = reportWithParameters("officeId", "loanOfficerId");
        user = mock(AppUser.class);
        when(context.authenticatedUser()).thenReturn(user);
        when(reportRepositoryWrapper.findOneThrowExceptionIfNotFound(1L)).thenReturn(report);
    }

    @Test
    void createRejectsUserWithoutReportPermission() {
        when(user.hasNotPermissionForReport(REPORT_NAME)).thenReturn(true);
        final JsonCommand command = mock(JsonCommand.class);
        when(command.longValueOfParameterNamed(ReportMailingJobConstants.STRETCHY_REPORT_ID_PARAM_NAME)).thenReturn(1L);

        final NoAuthorizationException exception = assertThrows(NoAuthorizationException.class,
                () -> service.createReportMailingJob(command));

        assertEquals("Not authorised to run report: " + REPORT_NAME, exception.getMessage());
        verify(reportMailingJobRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsUserWithoutReportPermissionAndKeepsRunAsUser() {
        when(user.hasNotPermissionForReport(REPORT_NAME)).thenReturn(true);
        final AppUser owner = mock(AppUser.class);
        final ReportMailingJob job = new ReportMailingJob().setStretchyReport(report).setRunAsUser(owner);
        final ReportMailingJob spyJob = spy(job);
        final JsonCommand command = mock(JsonCommand.class);
        doReturn(new LinkedHashMap<String, Object>(
                Map.of(ReportMailingJobConstants.EMAIL_RECIPIENTS_PARAM_NAME, "mallory@example.com"))).when(spyJob).update(command);
        when(reportMailingJobRepositoryWrapper.findOneThrowExceptionIfNotFound(5L)).thenReturn(spyJob);

        assertThrows(NoAuthorizationException.class, () -> service.updateReportMailingJob(5L, command));

        assertEquals(owner, spyJob.getRunAsUser());
        verify(reportMailingJobRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateByPermittedUserRunsJobAsThatUser() {
        when(user.hasNotPermissionForReport(REPORT_NAME)).thenReturn(false);
        final AppUser owner = mock(AppUser.class);
        final ReportMailingJob spyJob = spy(new ReportMailingJob().setStretchyReport(report).setRunAsUser(owner));
        final JsonCommand command = mock(JsonCommand.class);
        doReturn(new LinkedHashMap<String, Object>(
                Map.of(ReportMailingJobConstants.EMAIL_RECIPIENTS_PARAM_NAME, "ops@example.com"))).when(spyJob).update(command);
        when(reportMailingJobRepositoryWrapper.findOneThrowExceptionIfNotFound(5L)).thenReturn(spyJob);

        service.updateReportMailingJob(5L, command);

        assertEquals(user, spyJob.getRunAsUser());
        verify(reportMailingJobRepository).saveAndFlush(spyJob);
    }

    @Test
    void deactivationDoesNotRequireReportPermission() {
        when(user.hasNotPermissionForReport(REPORT_NAME)).thenReturn(true);
        final AppUser owner = mock(AppUser.class);
        final ReportMailingJob spyJob = spy(new ReportMailingJob().setStretchyReport(report).setRunAsUser(owner));
        final JsonCommand command = mock(JsonCommand.class);
        doReturn(new LinkedHashMap<String, Object>(
                Map.of(ReportMailingJobConstants.IS_ACTIVE_PARAM_NAME, false))).when(spyJob).update(command);
        when(reportMailingJobRepositoryWrapper.findOneThrowExceptionIfNotFound(5L)).thenReturn(spyJob);

        assertDoesNotThrow(() -> service.updateReportMailingJob(5L, command));
        assertEquals(owner, spyJob.getRunAsUser());
    }

    @Test
    void paramMapAcceptsDeclaredParametersAndOutputOptions() {
        assertDoesNotThrow(() -> validator.validateStretchyReportParamMapForReport(
                "{\"R_officeId\":\"1\",\"R_loanOfficerId\":\"-1\",\"exportCSV\":\"true\"}", report));
        assertDoesNotThrow(() -> validator.validateStretchyReportParamMapForReport(null, report));
    }

    @Test
    void paramMapRejectsUndeclaredOrReservedParameters() {
        assertEquals(List.of("R_userId"),
                validator.findUndeclaredStretchyReportParams(Map.of("R_officeId", "1", "R_userId", "1 or 1=1"), report));
        assertEquals(List.of("isSelfServiceUserReport"),
                validator.findUndeclaredStretchyReportParams(Map.of("isSelfServiceUserReport", "true"), report));
        assertEquals(List.of("officeId"), validator.findUndeclaredStretchyReportParams(Map.of("officeId", "1"), report));
        assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateStretchyReportParamMapForReport("{\"R_currentUserHierarchy\":\".\"}", report));
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateStretchyReportParamMapForReport("not json", report));
    }

    static Report reportWithParameters(final String... variables) {
        final Report report = mock(Report.class);
        when(report.getReportName()).thenReturn(REPORT_NAME);
        final Set<ReportParameterUsage> usages = new HashSet<>();
        for (String variable : variables) {
            final ReportParameter parameter = mock(ReportParameter.class);
            when(parameter.getParameterVariable()).thenReturn(variable);
            final ReportParameterUsage usage = mock(ReportParameterUsage.class);
            when(usage.getParameter()).thenReturn(parameter);
            usages.add(usage);
        }
        when(report.getReportParameterUsages()).thenReturn(Set.copyOf(usages));
        return report;
    }
}
