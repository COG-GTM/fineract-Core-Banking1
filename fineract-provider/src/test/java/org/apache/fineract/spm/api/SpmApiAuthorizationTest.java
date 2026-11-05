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
package org.apache.fineract.spm.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.spm.data.LookupTableData;
import org.apache.fineract.spm.data.ScorecardData;
import org.apache.fineract.spm.data.SurveyData;
import org.apache.fineract.spm.domain.Survey;
import org.apache.fineract.spm.service.LookupTableService;
import org.apache.fineract.spm.service.ScorecardReadPlatformService;
import org.apache.fineract.spm.service.ScorecardService;
import org.apache.fineract.spm.service.SpmService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpmApiAuthorizationTest {

    @Mock
    private PlatformSecurityContext securityContext;
    @Mock
    private AppUser user;
    @Mock
    private SpmService spmService;
    @Mock
    private ScorecardService scorecardService;
    @Mock
    private ScorecardReadPlatformService scorecardReadPlatformService;
    @Mock
    private LookupTableService lookupTableService;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;

    private SpmApiResource spmApiResource;
    private ScorecardApiResource scorecardApiResource;
    private LookupTableApiResource lookupTableApiResource;

    @BeforeEach
    void setUp() {
        when(securityContext.authenticatedUser()).thenReturn(user);
        spmApiResource = new SpmApiResource(securityContext, spmService);
        scorecardApiResource = new ScorecardApiResource(securityContext, spmService, scorecardService, clientRepositoryWrapper,
                scorecardReadPlatformService);
        lookupTableApiResource = new LookupTableApiResource(securityContext, spmService, lookupTableService);
    }

    private void denyAllSpmPermissions() {
        final NoAuthorizationException denied = new NoAuthorizationException("denied");
        lenient().doThrow(denied).when(user).validateHasReadPermission(anyString());
        lenient().doThrow(denied).when(user).validateHasPermissionTo(anyString());
    }

    @Test
    void surveyEndpointsRejectUserWithoutPermissions() {
        denyAllSpmPermissions();

        assertThrows(NoAuthorizationException.class, () -> spmApiResource.fetchAllSurveys(null));
        assertThrows(NoAuthorizationException.class, () -> spmApiResource.findSurvey(1L));
        assertThrows(NoAuthorizationException.class, () -> spmApiResource.createSurvey(new SurveyData()));
        assertThrows(NoAuthorizationException.class, () -> spmApiResource.editSurvey(1L, new SurveyData()));
        assertThrows(NoAuthorizationException.class, () -> spmApiResource.activateOrDeactivateSurvey(1L, "activate"));
        assertThrows(NoAuthorizationException.class, () -> spmApiResource.activateOrDeactivateSurvey(1L, "deactivate"));

        verifyNoInteractions(spmService);
    }

    @Test
    void scorecardEndpointsRejectUserWithoutPermissions() {
        denyAllSpmPermissions();

        assertThrows(NoAuthorizationException.class, () -> scorecardApiResource.findBySurvey(1L));
        assertThrows(NoAuthorizationException.class, () -> scorecardApiResource.findByClient(1L));
        assertThrows(NoAuthorizationException.class, () -> scorecardApiResource.findBySurveyAndClient(1L, 1L));
        assertThrows(NoAuthorizationException.class, () -> scorecardApiResource.createScorecard(1L, new ScorecardData()));

        verifyNoInteractions(spmService, clientRepositoryWrapper, scorecardService, scorecardReadPlatformService);
    }

    @Test
    void lookupTableEndpointsRejectUserWithoutPermissions() {
        denyAllSpmPermissions();

        assertThrows(NoAuthorizationException.class, () -> lookupTableApiResource.fetchLookupTables(1L));
        assertThrows(NoAuthorizationException.class, () -> lookupTableApiResource.findLookupTable(1L, "key"));
        assertThrows(NoAuthorizationException.class, () -> lookupTableApiResource.createLookupTable(1L, new LookupTableData()));

        verifyNoInteractions(spmService, lookupTableService);
    }

    @Test
    void handlersCheckTheExpectedPermissions() {
        when(spmService.findById(anyLong())).thenReturn(new Survey());

        spmApiResource.fetchAllSurveys(null);
        verify(user).validateHasReadPermission("SURVEY");
        spmApiResource.createSurvey(new SurveyData());
        verify(user).validateHasPermissionTo("CREATE_SURVEY");
        spmApiResource.activateOrDeactivateSurvey(1L, "activate");
        verify(user).validateHasPermissionTo("ACTIVATE_SURVEY");
        spmApiResource.activateOrDeactivateSurvey(1L, "deactivate");
        verify(user).validateHasPermissionTo("DEACTIVATE_SURVEY");
        scorecardApiResource.findBySurvey(1L);
        verify(user).validateHasReadPermission("SCORECARD");
        lookupTableApiResource.fetchLookupTables(1L);
        verify(user).validateHasReadPermission("LOOKUPTABLE");
    }

    @Test
    void clientScorecardReadOutsideUserOfficeHierarchyIsRejected() {
        final Client client = mock(Client.class);
        final Office office = mock(Office.class);
        when(office.getHierarchy()).thenReturn(".2.");
        when(client.getOffice()).thenReturn(office);
        when(clientRepositoryWrapper.findOneWithNotFoundDetection(anyLong())).thenReturn(client);
        doThrow(new NoAuthorizationException("out of hierarchy")).when(securityContext).validateAccessRights(".2.");

        assertThrows(NoAuthorizationException.class, () -> scorecardApiResource.findByClient(7L));
        assertThrows(NoAuthorizationException.class, () -> scorecardApiResource.findBySurveyAndClient(1L, 7L));

        verify(user, times(2)).validateHasReadPermission("SCORECARD");
        verifyNoInteractions(scorecardReadPlatformService);
        verify(spmService).findById(any());
    }
}
