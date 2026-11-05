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
package org.apache.fineract.infrastructure.survey.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.service.GenericDataService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.survey.data.ClientScoresOverview;
import org.apache.fineract.infrastructure.survey.data.SurveyData;
import org.apache.fineract.infrastructure.survey.service.ReadSurveyService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SurveyApiResourceTest {

    private static final String DATATABLE = "client_kyc_details";
    private static final Long CLIENT_ID = 7L;
    private static final Long ENTRY_ID = 1L;

    @Mock
    private DefaultToApiJsonSerializer<SurveyData> toApiJsonSerializer;
    @Mock
    private DefaultToApiJsonSerializer<ClientScoresOverview> toApiJsonClientScoreOverviewSerializer;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private ReadSurveyService readSurveyService;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    @Mock
    private GenericDataService genericDataService;
    @Mock
    private AppUser user;

    private SurveyApiResource underTest;

    @BeforeEach
    void setUp() {
        underTest = new SurveyApiResource(toApiJsonSerializer, toApiJsonClientScoreOverviewSerializer, context, readSurveyService,
                commandsSourceWritePlatformService, genericDataService);
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void getSurveyEntryRejectsUserWithoutDatatableReadPermission() {
        doThrow(new NoAuthorizationException("Not authorised to read datatable: " + DATATABLE)).when(user)
                .validateHasDatatableReadPermission(DATATABLE);

        assertThrows(NoAuthorizationException.class, () -> underTest.getSurveyEntry(DATATABLE, CLIENT_ID, ENTRY_ID));

        verify(user).validateHasReadPermission(SurveyApiConstants.SURVEY_RESOURCE_NAME);
        verifyNoInteractions(readSurveyService, genericDataService);
    }

    @Test
    void getSurveyEntryRejectsUserWithoutSurveyReadPermission() {
        doThrow(new NoAuthorizationException("User has no authority to READ surveys")).when(user)
                .validateHasReadPermission(SurveyApiConstants.SURVEY_RESOURCE_NAME);

        assertThrows(NoAuthorizationException.class, () -> underTest.getSurveyEntry(DATATABLE, CLIENT_ID, ENTRY_ID));

        verifyNoInteractions(readSurveyService, genericDataService);
    }

    @Test
    void getSurveyEntryReturnsDataWhenUserHasBothPermissions() {
        final GenericResultsetData results = new GenericResultsetData(null, null);
        when(readSurveyService.retrieveSurveyEntry(DATATABLE, CLIENT_ID, ENTRY_ID)).thenReturn(results);
        when(genericDataService.generateJsonFromGenericResultsetData(results)).thenReturn("[]");

        assertEquals("[]", underTest.getSurveyEntry(DATATABLE, CLIENT_ID, ENTRY_ID));

        verify(user).validateHasReadPermission(SurveyApiConstants.SURVEY_RESOURCE_NAME);
        verify(user).validateHasDatatableReadPermission(DATATABLE);
    }
}
