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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.apache.fineract.infrastructure.contentstore.data.ContentStoreType;
import org.apache.fineract.infrastructure.contentstore.service.ContentStoreService;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentCreateRequest;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentDeleteRequest;
import org.apache.fineract.infrastructure.documentmanagement.data.DocumentUpdateRequest;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.documentmanagement.domain.DocumentRepository;
import org.apache.fineract.infrastructure.documentmanagement.exception.InvalidDocumentEntityTypeException;
import org.apache.fineract.infrastructure.documentmanagement.mapping.DocumentMapper;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private BusinessEventNotifierService eventService;
    @Mock
    private FineractProperties properties;

    private DocumentWritePlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        underTest = new DocumentWritePlatformServiceImpl(documentRepository, documentMapper, storeService, eventService, properties);
    }

    @ParameterizedTest
    @ValueSource(strings = { "notes", "anything", "clients/../loans", "" })
    void createDocumentRejectsUnsupportedEntityType(final String entityType) {
        final var request = createRequest(entityType);

        assertThrows(InvalidDocumentEntityTypeException.class, () -> underTest.createDocument(request));

        verifyNoInteractions(storeService, documentRepository, eventService);
    }

    @Test
    void updateDocumentRejectsUnsupportedEntityType() {
        final var request = DocumentUpdateRequest.builder().id(1L).entityType("notes").entityId(1L).name("changed").build();

        assertThrows(InvalidDocumentEntityTypeException.class, () -> underTest.updateDocument(request));

        verifyNoInteractions(storeService, documentRepository);
    }

    @Test
    void deleteDocumentRejectsUnsupportedEntityType() {
        final var request = DocumentDeleteRequest.builder().id(1L).entityType("notes").entityId(1L).build();

        assertThrows(InvalidDocumentEntityTypeException.class, () -> underTest.deleteDocument(request));

        verifyNoInteractions(storeService, documentRepository, eventService);
    }

    @ParameterizedTest
    @ValueSource(strings = { "clients", "client_identifiers", "staff", "loans", "savings", "groups", "import", "IMPORT" })
    void createDocumentAcceptsSupportedEntityType(final String entityType) {
        when(storeService.getDelimiter()).thenReturn("/");
        when(storeService.getType()).thenReturn(ContentStoreType.FILE_SYSTEM);
        when(storeService.upload(anyString(), any(InputStream.class), anyString())).thenAnswer(i -> i.getArgument(0));
        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));

        final var response = underTest.createDocument(createRequest(entityType));

        assertThat(response.getResourceIdentifier()).isEqualTo(entityType);
    }

    private static DocumentCreateRequest createRequest(final String entityType) {
        final var stream = new ByteArrayInputStream(new byte[] { 1, 2, 3 });

        return DocumentCreateRequest.builder().entityType(entityType).entityId(1L).fileName("a.pdf").type("pdf").stream(stream).build();
    }
}
