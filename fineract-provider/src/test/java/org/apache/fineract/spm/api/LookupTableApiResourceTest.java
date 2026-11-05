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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.spm.data.LookupTableData;
import org.apache.fineract.spm.domain.Survey;
import org.apache.fineract.spm.exception.LookupTableNotFoundException;
import org.apache.fineract.spm.service.LookupTableService;
import org.apache.fineract.spm.service.SpmService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LookupTableApiResourceTest {

    private static final String PERMISSION_RESOURCE = "LOOKUPTABLE";
    private static final Long SURVEY_ID = 3L;

    @Mock
    private PlatformSecurityContext securityContext;
    @Mock
    private AppUser user;
    @Mock
    private SpmService spmService;
    @Mock
    private LookupTableService lookupTableService;
    @Mock
    private Survey survey;

    private LookupTableApiResource resource;

    @BeforeEach
    void setUp() {
        when(securityContext.authenticatedUser()).thenReturn(user);
        resource = new LookupTableApiResource(securityContext, spmService, lookupTableService);
    }

    @Test
    void createLookupTableWithoutCreatePermissionIsRejectedBeforeAnyWrite() {
        doThrow(new NoAuthorizationException("denied")).when(user).validateHasCreatePermission(PERMISSION_RESOURCE);

        assertThrows(NoAuthorizationException.class, () -> resource.createLookupTable(SURVEY_ID, lookupTableData()));

        verifyNoInteractions(spmService, lookupTableService);
    }

    @Test
    void fetchLookupTablesWithoutReadPermissionIsRejected() {
        doThrow(new NoAuthorizationException("denied")).when(user).validateHasReadPermission(PERMISSION_RESOURCE);

        assertThrows(NoAuthorizationException.class, () -> resource.fetchLookupTables(SURVEY_ID));

        verifyNoInteractions(spmService, lookupTableService);
    }

    @Test
    void findLookupTableWithoutReadPermissionIsRejected() {
        doThrow(new NoAuthorizationException("denied")).when(user).validateHasReadPermission(PERMISSION_RESOURCE);

        assertThrows(NoAuthorizationException.class, () -> resource.findLookupTable(SURVEY_ID, "PPI"));

        verifyNoInteractions(spmService, lookupTableService);
    }

    @Test
    void findLookupTableOnlyMatchesKeysOfTheRequestedSurvey() {
        when(spmService.findById(SURVEY_ID)).thenReturn(survey);
        when(lookupTableService.findBySurveyAndKey(survey, "PPI4")).thenReturn(Collections.emptyList());

        assertThrows(LookupTableNotFoundException.class, () -> resource.findLookupTable(SURVEY_ID, "PPI4"));

        verify(user).validateHasReadPermission(PERMISSION_RESOURCE);
        verify(lookupTableService).findBySurveyAndKey(survey, "PPI4");
        verifyNoMoreInteractions(lookupTableService);
    }

    @Test
    void createLookupTableWithCreatePermissionPersistsEntries() {
        when(spmService.findById(SURVEY_ID)).thenReturn(survey);

        resource.createLookupTable(SURVEY_ID, lookupTableData());

        verify(user).validateHasCreatePermission(PERMISSION_RESOURCE);
        verify(lookupTableService).createLookupTable(anyList());
    }

    private static LookupTableData lookupTableData() {
        return new LookupTableData("PPI", "score bands", Collections.emptyList());
    }
}
