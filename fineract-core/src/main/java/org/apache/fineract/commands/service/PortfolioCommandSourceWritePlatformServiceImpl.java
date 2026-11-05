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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.exception.CommandNotAwaitingApprovalException;
import org.apache.fineract.commands.exception.CommandNotFoundException;
import org.apache.fineract.commands.exception.UnsupportedCommandException;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.dataqueries.service.CleanupService;
import org.apache.fineract.infrastructure.jobs.service.SchedulerJobRunnerReadService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioCommandSourceWritePlatformServiceImpl implements PortfolioCommandSourceWritePlatformService {

    private static final String HEAD_OFFICE_HIERARCHY = ".";
    private static final String OFFICE_ID_PARAM = "officeId";
    private static final String CLIENT_ID_PARAM = "clientId";
    private static final String GROUP_ID_PARAM = "groupId";
    private static final String OFFICE_HIERARCHY_SQL = "select o.hierarchy from m_office o where o.id = ?";
    private static final String CLIENT_OFFICE_SQL = "select c.office_id from m_client c where c.id = ?";
    private static final String GROUP_OFFICE_SQL = "select g.office_id from m_group g where g.id = ?";
    private static final String LOAN_OFFICE_SQL = "select coalesce(c.office_id, g.office_id) from m_loan l"
            + " left join m_client c on c.id = l.client_id left join m_group g on g.id = l.group_id where l.id = ?";
    private static final String SAVINGS_OFFICE_SQL = "select coalesce(c.office_id, g.office_id) from m_savings_account s"
            + " left join m_client c on c.id = s.client_id left join m_group g on g.id = s.group_id where s.id = ?";

    private final PlatformSecurityContext context;
    private final CommandSourceRepository commandSourceRepository;
    private final FromJsonHelper fromApiJsonHelper;
    private final CommandProcessingService processAndLogCommandService;
    private final SchedulerJobRunnerReadService schedulerJobRunnerReadService;
    private final ConfigurationDomainService configurationService;
    private final List<CleanupService> cleanupServices;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public CommandProcessingResult logCommandSource(final CommandWrapper wrapper) {
        boolean isApprovedByChecker = false;

        // check if is update of own account details
        if (wrapper.isChangeOfOwnUserDetails(this.context.authenticatedUser(wrapper).getId())) {
            // then allow this operation to proceed.
            // maker checker doesnt mean anything here.
            isApprovedByChecker = true; // set to true in case permissions have
                                        // been maker-checker enabled by
                                        // accident.
        } else {
            // if not user changing their own details - check user has
            // permission to perform specific task.
            this.context.authenticatedUser(wrapper).validateHasPermissionTo(wrapper.getTaskPermissionName());
        }
        validateIsUpdateAllowed();

        final String json = wrapper.getJson();
        final JsonElement parsedCommand = this.fromApiJsonHelper.parse(json);
        JsonCommand command = JsonCommand.from(json, parsedCommand, this.fromApiJsonHelper, wrapper.getEntityName(), wrapper.getEntityId(),
                wrapper.getSubentityId(), wrapper.getGroupId(), wrapper.getClientId(), wrapper.getLoanId(), wrapper.getSavingsId(),
                wrapper.getTransactionId(), wrapper.getHref(), wrapper.getProductId(), wrapper.getCreditBureauId(),
                wrapper.getOrganisationCreditBureauId(), wrapper.getJobName(), wrapper.getLoanExternalId());

        return this.processAndLogCommandService.executeCommand(wrapper, command, isApprovedByChecker);
    }

    @Override
    public CommandProcessingResult approveEntry(final Long makerCheckerId) {
        final CommandSource commandSourceInput = validateMakerCheckerTransaction(makerCheckerId);
        validateIsUpdateAllowed();

        final CommandWrapper wrapper = CommandWrapper.fromExistingCommand(makerCheckerId, commandSourceInput.getActionName(),
                commandSourceInput.getEntityName(), commandSourceInput.getResourceId(), commandSourceInput.getSubResourceId(),
                commandSourceInput.getResourceGetUrl(), commandSourceInput.getProductId(), commandSourceInput.getOfficeId(),
                commandSourceInput.getGroupId(), commandSourceInput.getClientId(), commandSourceInput.getLoanId(),
                commandSourceInput.getSavingsId(), commandSourceInput.getTransactionId(), commandSourceInput.getCreditBureauId(),
                commandSourceInput.getOrganisationCreditBureauId(), commandSourceInput.getIdempotencyKey(),
                commandSourceInput.getLoanExternalId());
        final JsonElement parsedCommand = this.fromApiJsonHelper.parse(commandSourceInput.getCommandAsJson());
        final JsonCommand command = JsonCommand.fromExistingCommand(makerCheckerId, commandSourceInput.getCommandAsJson(), parsedCommand,
                this.fromApiJsonHelper, commandSourceInput.getEntityName(), commandSourceInput.getResourceId(),
                commandSourceInput.getSubResourceId(), commandSourceInput.getGroupId(), commandSourceInput.getClientId(),
                commandSourceInput.getLoanId(), commandSourceInput.getSavingsId(), commandSourceInput.getTransactionId(),
                commandSourceInput.getResourceGetUrl(), commandSourceInput.getProductId(), commandSourceInput.getCreditBureauId(),
                commandSourceInput.getOrganisationCreditBureauId(), commandSourceInput.getJobName(),
                commandSourceInput.getLoanExternalId());

        return this.processAndLogCommandService.executeCommand(wrapper, command, true);
    }

    @Transactional
    @Override
    public Long deleteEntry(final Long makerCheckerId) {

        validateMakerCheckerTransaction(makerCheckerId);
        validateIsUpdateAllowed();

        this.commandSourceRepository.deleteById(makerCheckerId);

        return makerCheckerId;
    }

    private CommandSource validateMakerCheckerTransaction(final Long makerCheckerId) {
        final CommandSource commandSource = this.commandSourceRepository.findById(makerCheckerId)
                .orElseThrow(() -> new CommandNotFoundException(makerCheckerId));
        AppUser appUser = this.context.authenticatedUser();
        validateCommandIsInUserOfficeHierarchy(commandSource, appUser);
        if (!commandSource.isAwaitingApproval()) {
            throw new CommandNotAwaitingApprovalException(makerCheckerId);
        }
        String permissionCode = commandSource.getPermissionCode();
        appUser.validateHasCheckerPermissionTo(permissionCode);
        if (!configurationService.isSameMakerCheckerEnabled() && !appUser.isCheckerSuperUser()) {
            AppUser maker = commandSource.getMaker();
            if (maker == null) {
                throw new UnsupportedCommandException(permissionCode, "Maker user is missing.");
            }
            if (Objects.equals(appUser.getId(), maker.getId())) {
                throw new UnsupportedCommandException(permissionCode, "Can not be checked by the same user.");
            }
        }
        return commandSource;
    }

    /**
     * Applies the same office data scoping as the maker-checker list: users outside the head office may only act on
     * commands that belong to an office within their own hierarchy. Out-of-scope commands are reported as not found.
     */
    private void validateCommandIsInUserOfficeHierarchy(final CommandSource commandSource, final AppUser appUser) {
        final String userHierarchy = appUser.getOffice() == null ? null : appUser.getOffice().getHierarchy();
        if (HEAD_OFFICE_HIERARCHY.equals(userHierarchy)) {
            return;
        }
        final OfficeScope officeScope = resolveCommandOffice(commandSource);
        if (!officeScope.scoped()) {
            return;
        }
        final String commandHierarchy = officeScope.officeId() == null ? null : findOfficeHierarchy(officeScope.officeId());
        if (userHierarchy == null || commandHierarchy == null || !commandHierarchy.startsWith(userHierarchy)) {
            throw new CommandNotFoundException(commandSource.getId());
        }
    }

    private OfficeScope resolveCommandOffice(final CommandSource commandSource) {
        if (commandSource.getOfficeId() != null) {
            return OfficeScope.of(commandSource.getOfficeId());
        }
        if (commandSource.getClientId() != null) {
            return OfficeScope.of(findSingleId(CLIENT_OFFICE_SQL, commandSource.getClientId()));
        }
        if (commandSource.getGroupId() != null) {
            return OfficeScope.of(findSingleId(GROUP_OFFICE_SQL, commandSource.getGroupId()));
        }
        if (commandSource.getLoanId() != null) {
            return OfficeScope.of(findSingleId(LOAN_OFFICE_SQL, commandSource.getLoanId()));
        }
        if (commandSource.getSavingsId() != null) {
            return OfficeScope.of(findSingleId(SAVINGS_OFFICE_SQL, commandSource.getSavingsId()));
        }
        return resolveCommandOfficeFromJson(commandSource.getCommandAsJson());
    }

    private OfficeScope resolveCommandOfficeFromJson(final String commandAsJson) {
        if (commandAsJson == null || commandAsJson.isBlank()) {
            return OfficeScope.UNSCOPED;
        }
        final JsonObject json;
        try {
            final JsonElement parsed = this.fromApiJsonHelper.parse(commandAsJson);
            if (parsed == null || !parsed.isJsonObject()) {
                return OfficeScope.UNSCOPED;
            }
            json = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return OfficeScope.UNSCOPED;
        }
        if (json.has(OFFICE_ID_PARAM)) {
            return OfficeScope.of(extractLong(json, OFFICE_ID_PARAM));
        }
        if (json.has(CLIENT_ID_PARAM)) {
            final Long clientId = extractLong(json, CLIENT_ID_PARAM);
            return OfficeScope.of(clientId == null ? null : findSingleId(CLIENT_OFFICE_SQL, clientId));
        }
        if (json.has(GROUP_ID_PARAM)) {
            final Long groupId = extractLong(json, GROUP_ID_PARAM);
            return OfficeScope.of(groupId == null ? null : findSingleId(GROUP_OFFICE_SQL, groupId));
        }
        return OfficeScope.UNSCOPED;
    }

    private Long extractLong(final JsonObject json, final String parameterName) {
        try {
            return this.fromApiJsonHelper.extractLongNamed(parameterName, json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Long findSingleId(final String sql, final Long id) {
        final List<Long> ids = this.jdbcTemplate.queryForList(sql, Long.class, id);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String findOfficeHierarchy(final Long officeId) {
        final List<String> hierarchies = this.jdbcTemplate.queryForList(OFFICE_HIERARCHY_SQL, String.class, officeId);
        return hierarchies.isEmpty() ? null : hierarchies.get(0);
    }

    /**
     * {@code scoped} is false for commands that are not tied to any office (e.g. product or configuration changes);
     * {@code officeId} is null when the command references an entity whose office could not be resolved.
     */
    private record OfficeScope(boolean scoped, Long officeId) {

        private static final OfficeScope UNSCOPED = new OfficeScope(false, null);

        private static OfficeScope of(final Long officeId) {
            return new OfficeScope(true, officeId);
        }
    }

    private void validateIsUpdateAllowed() {
        this.schedulerJobRunnerReadService.isUpdatesAllowed();
    }

    @Override
    public Long rejectEntry(final Long makerCheckerId) {
        final CommandSource commandSourceInput = validateMakerCheckerTransaction(makerCheckerId);
        validateIsUpdateAllowed();
        final AppUser maker = this.context.authenticatedUser();
        commandSourceInput.markAsRejected(maker);
        this.commandSourceRepository.save(commandSourceInput);
        if (cleanupServices != null) {
            for (CleanupService cleanupService : cleanupServices) {
                cleanupService.cleanup(commandSourceInput);
            }
        }
        return makerCheckerId;
    }
}
