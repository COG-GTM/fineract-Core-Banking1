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
package org.apache.fineract.infrastructure.bulkimport.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import org.apache.fineract.infrastructure.bulkimport.data.ImportData;
import org.apache.fineract.infrastructure.bulkimport.service.BulkImportWorkbookService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentContent;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentData;
import org.apache.fineract.infrastructure.documentmanagement.exception.DocumentNotFoundException;
import org.apache.fineract.infrastructure.documentmanagement.service.DocumentReadPlatformService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BulkImportApiResourceTest {

    private static final Long IMPORT_ID = 7L;
    private static final Long DOCUMENT_ID = 42L;
    private static final Long UPLOADER_ID = 1L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private BulkImportWorkbookService bulkImportWorkbookService;
    @Mock
    private DocumentReadPlatformService documentReadPlatformService;
    @Mock
    private DefaultToApiJsonSerializer<ImportData> toApiJsonSerializer;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private BulkImportApiResource underTest;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
    }

    private void denyReadImport() {
        doThrow(new NoAuthorizationException("User has no authority to: READ_IMPORT")).when(user).validateHasReadPermission("IMPORT");
    }

    @Test
    void downloadRequiresReadImportPermission() {
        denyReadImport();

        assertThrows(NoAuthorizationException.class, () -> underTest.getOutputTemplate(UPLOADER_ID));

        verifyNoInteractions(bulkImportWorkbookService, documentReadPlatformService);
    }

    @Test
    void locationRequiresReadImportPermission() {
        denyReadImport();

        assertThrows(NoAuthorizationException.class, () -> underTest.retriveOutputTemplateLocation(IMPORT_ID));

        verifyNoInteractions(bulkImportWorkbookService, documentReadPlatformService);
    }

    @Test
    void listRequiresReadImportPermission() {
        denyReadImport();

        assertThrows(NoAuthorizationException.class, () -> underTest.retrieveImportDocuments(uriInfo, "clients"));

        verifyNoInteractions(bulkImportWorkbookService, documentReadPlatformService);
    }

    @Test
    void downloadResolvesDocumentThroughImportId() {
        final var importData = ImportData.instance(IMPORT_ID, DOCUMENT_ID, null, null, true, "clients.xls", UPLOADER_ID, 1, 1, 0);
        final var document = new DocumentData();
        document.setId(DOCUMENT_ID);
        document.setParentEntityType("IMPORT");
        document.setParentEntityId(UPLOADER_ID);
        final var content = new DocumentContent();
        content.setFileName("clients.xls");
        content.setContentType("application/vnd.ms-excel");
        content.setStream(new ByteArrayInputStream(new byte[] { 1 }));
        when(bulkImportWorkbookService.getImport(IMPORT_ID)).thenReturn(importData);
        when(documentReadPlatformService.retrieveDocument(DOCUMENT_ID)).thenReturn(document);
        when(documentReadPlatformService.retrieveDocumentContent("IMPORT", UPLOADER_ID, DOCUMENT_ID)).thenReturn(content);

        final var response = underTest.getOutputTemplate(IMPORT_ID);

        assertEquals(200, response.getStatus());
        verify(documentReadPlatformService, never()).retrieveAllDocuments(anyString(), any());
    }

    @Test
    void downloadOfUnknownImportIdIsNotFound() {
        when(bulkImportWorkbookService.getImport(IMPORT_ID)).thenReturn(null);

        assertThrows(DocumentNotFoundException.class, () -> underTest.getOutputTemplate(IMPORT_ID));

        verify(documentReadPlatformService, never()).retrieveDocument(anyLong());
    }
}
