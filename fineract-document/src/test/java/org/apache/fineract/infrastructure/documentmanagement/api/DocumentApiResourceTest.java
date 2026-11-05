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
package org.apache.fineract.infrastructure.documentmanagement.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import org.apache.fineract.command.core.CommandPipeline;
import org.apache.fineract.infrastructure.contentstore.detector.ContentDetectorManager;
import org.apache.fineract.infrastructure.documentmanagement.exception.InvalidDocumentEntityTypeException;
import org.apache.fineract.infrastructure.documentmanagement.service.DocumentReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentApiResourceTest {

    private static final String UNSUPPORTED = "notes";

    @Mock
    private DocumentReadPlatformService documentReadPlatformService;
    @Mock
    private FileUploadValidator fileUploadValidator;
    @Mock
    private ContentDetectorManager contentDetectorManager;
    @Mock
    private CommandPipeline commandPipeline;

    private DocumentApiResource underTest;

    @BeforeEach
    void setUp() {
        underTest = new DocumentApiResource(documentReadPlatformService, fileUploadValidator, contentDetectorManager, commandPipeline);
    }

    @Test
    void everyEndpointRejectsUnsupportedEntityType() {
        final Class<InvalidDocumentEntityTypeException> invalid = InvalidDocumentEntityTypeException.class;

        assertThrows(invalid, () -> underTest.retrieveAllDocuments(UNSUPPORTED, 1L));
        assertThrows(invalid, () -> underTest.getDocument(UNSUPPORTED, 1L, 1L));
        assertThrows(invalid, () -> underTest.downloadFile(UNSUPPORTED, 1L, 1L));
        assertThrows(invalid, () -> underTest.createDocument(UNSUPPORTED, 1L, 3L, null, null, null, "name", "description"));
        assertThrows(invalid, () -> underTest.updateDocument(UNSUPPORTED, 1L, 1L, 3L, null, null, null, "name", "description"));
        assertThrows(invalid, () -> underTest.deleteDocument(UNSUPPORTED, 1L, 1L));

        verifyNoInteractions(documentReadPlatformService, fileUploadValidator, contentDetectorManager, commandPipeline);
    }
}
