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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import org.apache.fineract.infrastructure.contentstore.data.ContentStoreType;
import org.apache.fineract.infrastructure.contentstore.service.ContentStoreService;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentCreateRequest;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentDeleteRequest;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentUpdateRequest;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.documentmanagement.domain.DocumentRepository;
import org.apache.fineract.infrastructure.documentmanagement.exception.DocumentNotFoundException;
import org.apache.fineract.infrastructure.documentmanagement.mapping.DocumentMapper;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentWritePlatformServiceImplTest {

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
    @Mock
    private DocumentParentEntityAccessValidator parentEntityAccessValidator;

    private DocumentWritePlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        underTest = new DocumentWritePlatformServiceImpl(documentRepository, documentMapper, storeService, businessEventNotifierService,
                properties, parentEntityAccessValidator);
    }

    @Test
    void deleteRejectsDocumentOfAnotherEntity() {
        when(documentRepository.findByIdAndParentEntityTypeAndParentEntityId(987L, "clients", 1L)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class,
                () -> underTest.deleteDocument(DocumentDeleteRequest.builder().id(987L).entityType("clients").entityId(1L).build()));

        verify(parentEntityAccessValidator).validate("clients", 1L);
        verify(documentRepository, never()).findById(anyLong());
        verify(documentRepository, never()).deleteById(anyLong());
        verify(storeService, never()).delete(anyString());
    }

    @Test
    void deleteRejectsEntityOutsideUserOffice() {
        doThrow(new NoAuthorizationException("denied")).when(parentEntityAccessValidator).validate("clients", 4711L);

        assertThrows(NoAuthorizationException.class,
                () -> underTest.deleteDocument(DocumentDeleteRequest.builder().id(987L).entityType("clients").entityId(4711L).build()));

        verify(documentRepository, never()).deleteById(anyLong());
        verify(storeService, never()).delete(anyString());
    }

    @Test
    void deleteRemovesDocumentOfUrlEntity() {
        final var doc = new Document().setId(987L).setParentEntityType("clients").setParentEntityId(1L)
                .setLocation("documents/clients/1/a");
        when(documentRepository.findByIdAndParentEntityTypeAndParentEntityId(987L, "clients", 1L)).thenReturn(Optional.of(doc));

        final var response = underTest
                .deleteDocument(DocumentDeleteRequest.builder().id(987L).entityType("clients").entityId(1L).build());

        assertEquals(987L, response.getResourceId());
        verify(storeService).delete("documents/clients/1/a");
        verify(documentRepository).deleteById(987L);
    }

    @Test
    void updateRejectsDocumentOfAnotherEntity() {
        when(documentRepository.findByIdAndParentEntityTypeAndParentEntityId(987L, "loans", 1L)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> underTest
                .updateDocument(DocumentUpdateRequest.builder().id(987L).entityType("loans").entityId(1L).name("replaced").build()));

        verify(parentEntityAccessValidator).validate("loans", 1L);
        verify(documentRepository, never()).findById(anyLong());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void updateRejectsEntityOutsideUserOffice() {
        doThrow(new NoAuthorizationException("denied")).when(parentEntityAccessValidator).validate("loans", 4711L);

        assertThrows(NoAuthorizationException.class, () -> underTest
                .updateDocument(DocumentUpdateRequest.builder().id(987L).entityType("loans").entityId(4711L).name("replaced").build()));

        verify(documentRepository, never()).save(any());
    }

    @Test
    void updateChangesDocumentOfUrlEntity() {
        final var doc = new Document().setId(987L).setParentEntityType("loans").setParentEntityId(1L).setName("old");
        when(documentRepository.findByIdAndParentEntityTypeAndParentEntityId(987L, "loans", 1L)).thenReturn(Optional.of(doc));
        when(storeService.getType()).thenReturn(ContentStoreType.FILE_SYSTEM);

        underTest.updateDocument(DocumentUpdateRequest.builder().id(987L).entityType("loans").entityId(1L).name("new").build());

        assertEquals("new", doc.getName());
        verify(documentRepository).save(doc);
    }

    @Test
    void createRejectsEntityOutsideUserOffice() {
        doThrow(new NoAuthorizationException("denied")).when(parentEntityAccessValidator).validate("clients", 4711L);

        assertThrows(NoAuthorizationException.class,
                () -> underTest.createDocument(DocumentCreateRequest.builder().entityType("clients").entityId(4711L).fileName("id.pdf")
                        .type("application/pdf").stream(new ByteArrayInputStream(new byte[] { 1 })).build()));

        verify(storeService, never()).upload(anyString(), any(InputStream.class), anyString());
        verify(documentRepository, never()).save(any());
    }
}
