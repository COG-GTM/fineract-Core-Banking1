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
package org.apache.fineract.investor.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.PagedRequest;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.investor.api.search.ExternalAssetOwnersSearchApiDelegate;
import org.apache.fineract.investor.service.ExternalAssetOwnersReadService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformServiceCommon;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.useradministration.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;

@ExtendWith(MockitoExtension.class)
class ExternalAssetOwnersApiResourceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private ExternalAssetOwnersReadService readService;
    @Mock
    private DefaultToApiJsonSerializer<String> serializer;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    @Mock
    private LoanReadPlatformServiceCommon loanReadPlatformService;
    @Mock
    private ExternalAssetOwnersSearchApiDelegate searchDelegate;

    private ExternalAssetOwnersApiResource resource;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        resource = new ExternalAssetOwnersApiResource(context, readService, serializer, commandsSourceWritePlatformService,
                loanReadPlatformService, searchDelegate);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    private static Stream<Arguments> ownerAndTransferReads() {
        return Stream.of(
                Arguments.of("getTransfers", (Consumer<ExternalAssetOwnersApiResource>) r -> r.getTransfers(null, 1L, null, 0, 10)),
                Arguments.of("getActiveTransfer", (Consumer<ExternalAssetOwnersApiResource>) r -> r.getActiveTransfer(null, 1L, null)),
                Arguments.of("searchInvestorData",
                        (Consumer<ExternalAssetOwnersApiResource>) r -> r.searchInvestorData(new PagedRequest<>())),
                Arguments.of("retrieveExternalAssetOwners",
                        (Consumer<ExternalAssetOwnersApiResource>) ExternalAssetOwnersApiResource::retrieveExternalAssetOwners));
    }

    private static Stream<Arguments> journalEntryReads() {
        return Stream.of(
                Arguments.of("getJournalEntriesOfTransfer",
                        (Consumer<ExternalAssetOwnersApiResource>) r -> r.getJournalEntriesOfTransfer(1L, 0, 10)),
                Arguments.of("getJournalEntriesOfOwner",
                        (Consumer<ExternalAssetOwnersApiResource>) r -> r.getJournalEntriesOfOwner("owner-1", 0, 10)));
    }

    private static Stream<Arguments> allReads() {
        return Stream.concat(ownerAndTransferReads(), journalEntryReads());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allReads")
    void readEndpointsRejectUserWithoutPermissions(String name, Consumer<ExternalAssetOwnersApiResource> call) {
        authenticateWith();

        assertThrows(NoAuthorizationException.class, () -> call.accept(resource));
        verifyNoInteractions(readService, searchDelegate);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ownerAndTransferReads")
    void ownerAndTransferReadsAllowedWithReadExternalAssetOwner(String name, Consumer<ExternalAssetOwnersApiResource> call) {
        authenticateWith(new Permission("investor", "EXTERNAL_ASSET_OWNER", "READ"));

        assertDoesNotThrow(() -> call.accept(resource));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("journalEntryReads")
    void journalEntryReadsRequireJournalEntryReadPermissionToo(String name, Consumer<ExternalAssetOwnersApiResource> call) {
        authenticateWith(new Permission("investor", "EXTERNAL_ASSET_OWNER", "READ"));

        assertThrows(NoAuthorizationException.class, () -> call.accept(resource));
        verifyNoInteractions(readService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("journalEntryReads")
    void journalEntryReadsAllowedWithBothReadPermissions(String name, Consumer<ExternalAssetOwnersApiResource> call) {
        authenticateWith(new Permission("investor", "EXTERNAL_ASSET_OWNER", "READ"), new Permission("accounting", "JOURNALENTRY", "READ"));

        assertDoesNotThrow(() -> call.accept(resource));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allReads")
    void readEndpointsAllowedWithAllFunctionsRead(String name, Consumer<ExternalAssetOwnersApiResource> call) {
        authenticateWith(new Permission("special", "ALL_FUNCTIONS_READ", null) {

            @Override
            public boolean hasCode(String checkCode) {
                return "ALL_FUNCTIONS_READ".equalsIgnoreCase(checkCode);
            }
        });

        assertDoesNotThrow(() -> call.accept(resource));
    }

    @Test
    void retrieveExternalAssetOwnersDelegatesToReadServiceWhenPermitted() {
        authenticateWith(new Permission("investor", "EXTERNAL_ASSET_OWNER", "READ"));

        resource.retrieveExternalAssetOwners();

        verify(readService).retrieveAllExternalOwners();
    }

    private void authenticateWith(Permission... permissions) {
        Role role = new Role("test-role", "test role");
        for (Permission permission : permissions) {
            role.updatePermission(permission, true);
        }
        AppUser user = new AppUser(null, new User("mallory", "", List.of()), Set.of(role), "mallory@example.com", "Mallory", "Teller", null,
                true, false, Collections.emptyList(), false);
        when(context.authenticatedUser()).thenReturn(user);
    }
}
