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
package org.apache.fineract.infrastructure.documentmanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import org.apache.fineract.infrastructure.contentstore.data.ContentStoreType;
import org.apache.fineract.infrastructure.contentstore.exception.ContentPolicyException;
import org.apache.fineract.infrastructure.contentstore.service.ContentStoreService;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentUpdateRequest;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.documentmanagement.domain.DocumentRepository;
import org.apache.fineract.infrastructure.documentmanagement.mapping.DocumentMapper;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentWritePlatformServiceImplTest {

    private static final String ORIGINAL_LOCATION = "documents/clients/1/original.pdf";

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private ContentStoreService storeService;
    @Mock
    private BusinessEventNotifierService businessEventNotifierService;
    @Mock
    private FineractProperties properties;

    private DocumentWritePlatformServiceImpl underTest;
    private Document document;

    @BeforeEach
    void setUp() {
        underTest = new DocumentWritePlatformServiceImpl(documentRepository, documentMapper, storeService, businessEventNotifierService,
                properties);

        document = new Document().setParentEntityType("clients").setParentEntityId(1L).setLocation(ORIGINAL_LOCATION)
                .setFileName("original.pdf");

        when(storeService.getDelimiter()).thenReturn("/");
        when(storeService.getType()).thenReturn(ContentStoreType.FILE_SYSTEM);
        when(documentRepository.findById(5L)).thenReturn(Optional.of(document));
    }

    @Test
    void updateKeepsOriginalFileWhenReplacementIsRejected() {
        when(storeService.upload(anyString(), any(InputStream.class), anyString()))
                .thenThrow(new ContentPolicyException("Trying to overwrite a sibling file"));

        var request = DocumentUpdateRequest.builder().id(5L).entityType("clients").entityId(1L).fileName("../../evil.pdf")
                .type("application/pdf").size(3L).stream(new ByteArrayInputStream(new byte[] { 1, 2, 3 })).build();

        assertThrows(RuntimeException.class, () -> underTest.updateDocument(request));

        verify(storeService, never()).delete(anyString());
        assertEquals(ORIGINAL_LOCATION, document.getLocation());
    }

    @Test
    void updateDeletesOriginalFileAfterReplacementIsStored() {
        when(storeService.upload(anyString(), any(InputStream.class), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = DocumentUpdateRequest.builder().id(5L).entityType("clients").entityId(1L).fileName("replacement.pdf")
                .type("application/pdf").size(3L).stream(new ByteArrayInputStream(new byte[] { 1, 2, 3 })).build();

        underTest.updateDocument(request);

        verify(storeService).delete(ORIGINAL_LOCATION);
        assertEquals("documents/clients/1/replacement.pdf", document.getLocation());
    }
}
