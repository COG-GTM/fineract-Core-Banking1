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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.apache.fineract.spm.data.ScorecardData;
import org.apache.fineract.spm.service.ScorecardReadPlatformService;
import org.apache.fineract.spm.service.ScorecardService;
import org.apache.fineract.spm.service.SpmService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScorecardApiResourceTest {

    private static final Long SURVEY_ID = 2L;
    private static final Long CLIENT_ID = 4512L;
    private static final String USER_HIERARCHY = ".2.";

    @Mock
    private PlatformSecurityContext securityContext;
    @Mock
    private SpmService spmService;
    @Mock
    private ScorecardService scorecardService;
    @Mock
    private ClientRepositoryWrapper clientRepositoryWrapper;
    @Mock
    private ScorecardReadPlatformService scorecardReadPlatformService;
    @Mock
    private AppUser appUser;
    @Mock
    private Client client;

    @InjectMocks
    private ScorecardApiResource resource;

    @Test
    void findByClientRequiresReadScorecardPermission() {
        when(securityContext.authenticatedUser()).thenReturn(appUser);
        doThrow(new NoAuthorizationException("no")).when(appUser).validateHasReadPermission("SCORECARD");

        assertThrows(NoAuthorizationException.class, () -> resource.findByClient(CLIENT_ID));

        verifyNoInteractions(clientRepositoryWrapper, scorecardReadPlatformService);
    }

    @Test
    void findByClientRejectsClientOutsideUserOfficeHierarchy() {
        when(securityContext.authenticatedUser()).thenReturn(appUser);
        when(securityContext.officeHierarchy()).thenReturn(USER_HIERARCHY);
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(CLIENT_ID, USER_HIERARCHY + "%"))
                .thenThrow(new ClientNotFoundException(CLIENT_ID.toString(), "client.id"));

        assertThrows(ClientNotFoundException.class, () -> resource.findByClient(CLIENT_ID));

        verify(clientRepositoryWrapper, never()).findOneWithNotFoundDetection(anyLong());
        verifyNoInteractions(scorecardReadPlatformService);
    }

    @Test
    void findByClientReturnsScorecardsForClientInUserOfficeHierarchy() {
        final List<ScorecardData> scorecards = List.of();
        when(securityContext.authenticatedUser()).thenReturn(appUser);
        when(securityContext.officeHierarchy()).thenReturn(USER_HIERARCHY);
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(CLIENT_ID, USER_HIERARCHY + "%")).thenReturn(client);
        when(scorecardReadPlatformService.retrieveScorecardByClient(CLIENT_ID)).thenReturn(scorecards);

        assertSame(scorecards, resource.findByClient(CLIENT_ID));
        verify(appUser).validateHasReadPermission("SCORECARD");
    }

    @Test
    void findBySurveyAndClientRejectsClientOutsideUserOfficeHierarchy() {
        when(securityContext.authenticatedUser()).thenReturn(appUser);
        when(securityContext.officeHierarchy()).thenReturn(USER_HIERARCHY);
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(CLIENT_ID, USER_HIERARCHY + "%"))
                .thenThrow(new ClientNotFoundException(CLIENT_ID.toString(), "client.id"));

        assertThrows(ClientNotFoundException.class, () -> resource.findBySurveyAndClient(SURVEY_ID, CLIENT_ID));

        verify(appUser).validateHasReadPermission("SCORECARD");
        verifyNoInteractions(scorecardReadPlatformService);
    }

    @Test
    void findBySurveyRequiresReadScorecardPermission() {
        when(securityContext.authenticatedUser()).thenReturn(appUser);
        doThrow(new NoAuthorizationException("no")).when(appUser).validateHasReadPermission("SCORECARD");

        assertThrows(NoAuthorizationException.class, () -> resource.findBySurvey(SURVEY_ID));

        verifyNoInteractions(spmService, scorecardReadPlatformService);
    }

    @Test
    void createScorecardRequiresCreateScorecardPermission() {
        when(securityContext.authenticatedUser()).thenReturn(appUser);
        doThrow(new NoAuthorizationException("no")).when(appUser).validateHasPermissionTo("CREATE_SCORECARD");

        assertThrows(NoAuthorizationException.class, () -> resource.createScorecard(SURVEY_ID, scorecardDataFor(CLIENT_ID)));

        verifyNoInteractions(spmService, clientRepositoryWrapper, scorecardService);
    }

    @Test
    void createScorecardRejectsClientOutsideUserOfficeHierarchy() {
        when(securityContext.authenticatedUser()).thenReturn(appUser);
        when(securityContext.officeHierarchy()).thenReturn(USER_HIERARCHY);
        when(clientRepositoryWrapper.getClientByClientIdAndHierarchy(CLIENT_ID, USER_HIERARCHY + "%"))
                .thenThrow(new ClientNotFoundException(CLIENT_ID.toString(), "client.id"));

        assertThrows(ClientNotFoundException.class, () -> resource.createScorecard(SURVEY_ID, scorecardDataFor(CLIENT_ID)));

        verify(scorecardService, never()).createScorecard(any());
    }

    private static ScorecardData scorecardDataFor(final Long clientId) {
        return ScorecardData.instance(null, null, null, SURVEY_ID, null, clientId);
    }
}
