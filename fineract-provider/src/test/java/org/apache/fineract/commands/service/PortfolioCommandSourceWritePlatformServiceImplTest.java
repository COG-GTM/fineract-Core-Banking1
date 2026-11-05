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
package org.apache.fineract.commands.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.commands.domain.CommandProcessingResultType;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.exception.CommandNotFoundException;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.jobs.service.SchedulerJobRunnerReadService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

public class PortfolioCommandSourceWritePlatformServiceImplTest {

    private static final Long AUDIT_ID = 42L;
    private static final Long NORTH_OFFICE_ID = 2L;
    private static final Long SOUTH_OFFICE_ID = 3L;
    private static final Long SOUTH_SUB_OFFICE_ID = 4L;

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final CommandSourceRepository commandSourceRepository = mock(CommandSourceRepository.class);
    private final CommandProcessingService commandProcessingService = mock(CommandProcessingService.class);
    private final SchedulerJobRunnerReadService schedulerJobRunnerReadService = mock(SchedulerJobRunnerReadService.class);
    private final ConfigurationDomainService configurationService = mock(ConfigurationDomainService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private PortfolioCommandSourceWritePlatformServiceImpl underTest;

    @BeforeEach
    public void setUp() {
        underTest = new PortfolioCommandSourceWritePlatformServiceImpl(context, commandSourceRepository, new FromJsonHelper(),
                commandProcessingService, schedulerJobRunnerReadService, configurationService, List.of(), jdbcTemplate);
        when(configurationService.isSameMakerCheckerEnabled()).thenReturn(true);
        mockOfficeHierarchy(NORTH_OFFICE_ID, ".2.");
        mockOfficeHierarchy(SOUTH_OFFICE_ID, ".3.");
        mockOfficeHierarchy(SOUTH_SUB_OFFICE_ID, ".3.4.");
    }

    @Test
    public void branchCheckerCannotDeleteCommandOfSiblingOffice() {
        loginAs(".3.");
        givenAwaitingCommand(CommandSource.builder().officeId(NORTH_OFFICE_ID));

        assertThrows(CommandNotFoundException.class, () -> underTest.deleteEntry(AUDIT_ID));
        verify(commandSourceRepository, never()).deleteById(any());
    }

    @Test
    public void branchCheckerCannotApproveCommandOfSiblingOffice() {
        loginAs(".3.");
        givenAwaitingCommand(CommandSource.builder().officeId(NORTH_OFFICE_ID));

        assertThrows(CommandNotFoundException.class, () -> underTest.approveEntry(AUDIT_ID));
        verifyNoInteractions(commandProcessingService);
    }

    @Test
    public void branchCheckerCannotRejectCommandOfSiblingOffice() {
        loginAs(".3.");
        givenAwaitingCommand(CommandSource.builder().officeId(NORTH_OFFICE_ID));

        assertThrows(CommandNotFoundException.class, () -> underTest.rejectEntry(AUDIT_ID));
        verify(commandSourceRepository, never()).save(any());
    }

    @Test
    public void branchCheckerCannotApproveClientCreationForSiblingOfficeFromJsonBody() {
        loginAs(".3.");
        givenAwaitingCommand(CommandSource.builder().commandAsJson("{\"officeId\": 2, \"firstname\": \"Alice\"}"));

        assertThrows(CommandNotFoundException.class, () -> underTest.approveEntry(AUDIT_ID));
        verifyNoInteractions(commandProcessingService);
    }

    @Test
    public void branchCheckerCannotDeleteCommandOfClientInSiblingOffice() {
        loginAs(".3.");
        when(jdbcTemplate.queryForList(startsWith("select c.office_id from m_client"), eq(Long.class), eq(77L)))
                .thenReturn(List.of(NORTH_OFFICE_ID));
        givenAwaitingCommand(CommandSource.builder().clientId(77L));

        assertThrows(CommandNotFoundException.class, () -> underTest.deleteEntry(AUDIT_ID));
        verify(commandSourceRepository, never()).deleteById(any());
    }

    @Test
    public void branchCheckerCannotDeleteCommandOfLoanInSiblingOffice() {
        loginAs(".3.");
        when(jdbcTemplate.queryForList(startsWith("select coalesce(c.office_id, g.office_id) from m_loan"), eq(Long.class), eq(88L)))
                .thenReturn(List.of(NORTH_OFFICE_ID));
        givenAwaitingCommand(CommandSource.builder().loanId(88L));

        assertThrows(CommandNotFoundException.class, () -> underTest.deleteEntry(AUDIT_ID));
        verify(commandSourceRepository, never()).deleteById(any());
    }

    @Test
    public void branchCheckerCannotDeleteCommandWhoseOfficeCannotBeResolved() {
        loginAs(".3.");
        when(jdbcTemplate.queryForList(startsWith("select c.office_id from m_client"), eq(Long.class), anyLong())).thenReturn(List.of());
        givenAwaitingCommand(CommandSource.builder().clientId(999L));

        assertThrows(CommandNotFoundException.class, () -> underTest.deleteEntry(AUDIT_ID));
        verify(commandSourceRepository, never()).deleteById(any());
    }

    @Test
    public void branchCheckerCanDeleteCommandOfOwnOffice() {
        loginAs(".3.");
        givenAwaitingCommand(CommandSource.builder().officeId(SOUTH_OFFICE_ID));

        assertEquals(AUDIT_ID, underTest.deleteEntry(AUDIT_ID));
        verify(commandSourceRepository).deleteById(AUDIT_ID);
    }

    @Test
    public void branchCheckerCanDeleteCommandOfChildOffice() {
        loginAs(".3.");
        givenAwaitingCommand(CommandSource.builder().commandAsJson("{\"officeId\": 4}"));

        assertEquals(AUDIT_ID, underTest.deleteEntry(AUDIT_ID));
        verify(commandSourceRepository).deleteById(AUDIT_ID);
    }

    @Test
    public void subOfficeCheckerCannotDeleteCommandOfParentOffice() {
        loginAs(".3.4.");
        givenAwaitingCommand(CommandSource.builder().officeId(SOUTH_OFFICE_ID));

        assertThrows(CommandNotFoundException.class, () -> underTest.deleteEntry(AUDIT_ID));
        verify(commandSourceRepository, never()).deleteById(any());
    }

    @Test
    public void headOfficeCheckerCanDeleteCommandOfAnyOffice() {
        loginAs(".");
        givenAwaitingCommand(CommandSource.builder().officeId(NORTH_OFFICE_ID));

        assertEquals(AUDIT_ID, underTest.deleteEntry(AUDIT_ID));
        verify(commandSourceRepository).deleteById(AUDIT_ID);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    public void branchCheckerCanDeleteCommandNotTiedToAnyOffice() {
        loginAs(".3.");
        givenAwaitingCommand(CommandSource.builder().commandAsJson("{\"name\": \"Product\"}"));

        assertEquals(AUDIT_ID, underTest.deleteEntry(AUDIT_ID));
        verify(commandSourceRepository).deleteById(AUDIT_ID);
    }

    private void loginAs(final String officeHierarchy) {
        Office office = mock(Office.class);
        when(office.getHierarchy()).thenReturn(officeHierarchy);
        AppUser appUser = mock(AppUser.class);
        when(appUser.getOffice()).thenReturn(office);
        when(context.authenticatedUser()).thenReturn(appUser);
    }

    private void givenAwaitingCommand(final CommandSource.CommandSourceBuilder builder) {
        CommandSource commandSource = builder.actionName("CREATE").entityName("CLIENT")
                .status(CommandProcessingResultType.AWAITING_APPROVAL.getValue()).build();
        when(commandSourceRepository.findById(AUDIT_ID)).thenReturn(Optional.of(commandSource));
    }

    private void mockOfficeHierarchy(final Long officeId, final String hierarchy) {
        when(jdbcTemplate.queryForList(startsWith("select o.hierarchy from m_office"), eq(String.class), eq(officeId)))
                .thenReturn(List.of(hierarchy));
    }
}
