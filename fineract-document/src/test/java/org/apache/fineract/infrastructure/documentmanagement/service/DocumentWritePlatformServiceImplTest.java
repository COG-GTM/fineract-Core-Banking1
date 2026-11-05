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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import org.apache.fineract.infrastructure.contentstore.data.ContentStoreType;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentWritePlatformServiceImplTest {

    private static final String EXISTING_LOCATION = "documents/clients/17/AbCdEfGhIjKlMnOp/contract.pdf";

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

    private DocumentWritePlatformServiceImpl service;
    private Document document;

    @BeforeEach
    void setUp() {
        service = new DocumentWritePlatformServiceImpl(documentRepository, documentMapper, storeService, businessEventNotifierService,
                properties);
        document = new Document().setId(1L).setParentEntityType("clients").setParentEntityId(17L).setFileName("contract.pdf")
                .setLocation(EXISTING_LOCATION);

        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(storeService.getType()).thenReturn(ContentStoreType.S3);
    }

    @Test
    void sameFileNameUpdateOverwritesExistingLocationWithoutDeleting() {
        final InputStream stream = new ByteArrayInputStream(new byte[] { 1 });
        when(storeService.getDelimiter()).thenReturn("/");
        when(storeService.upload(EXISTING_LOCATION, stream, "application/pdf")).thenReturn(EXISTING_LOCATION);

        service.updateDocument(request("contract.pdf", stream));

        verify(storeService).upload(EXISTING_LOCATION, stream, "application/pdf");
        verify(storeService, never()).delete(anyString());
        assertEquals(EXISTING_LOCATION, document.getLocation());
    }

    @Test
    void renameUploadsNewObjectBeforeDeletingOldOne() {
        final InputStream stream = new ByteArrayInputStream(new byte[] { 1 });
        final var newLocation = "documents/clients/17/QrStUvWxYzAbCdEf/passport.pdf";
        when(storeService.getDelimiter()).thenReturn("/");
        when(storeService.upload(eq("documents/clients/17/passport.pdf"), eq(stream), eq("application/pdf"))).thenReturn(newLocation);

        service.updateDocument(request("passport.pdf", stream));

        final InOrder order = inOrder(storeService);
        order.verify(storeService).upload(eq("documents/clients/17/passport.pdf"), any(InputStream.class), eq("application/pdf"));
        order.verify(storeService).delete(EXISTING_LOCATION);
        assertEquals(newLocation, document.getLocation());
        assertEquals("passport.pdf", document.getFileName());
    }

    @Test
    void contentOnlyUpdatePersistsReturnedLocation() {
        final InputStream stream = new ByteArrayInputStream(new byte[] { 1 });
        final var restoredLocation = "documents/clients/17/AbCdEfGhIjKlMnOp/QrStUvWxYzAbCdEf/contract.pdf";
        when(storeService.upload(EXISTING_LOCATION, stream, "application/pdf")).thenReturn(restoredLocation);

        service.updateDocument(request(null, stream));

        verify(storeService, never()).delete(anyString());
        assertEquals(restoredLocation, document.getLocation());
    }

    private DocumentUpdateRequest request(String fileName, InputStream stream) {
        return DocumentUpdateRequest.builder().id(1L).entityType("clients").entityId(17L).fileName(fileName).type("application/pdf")
                .stream(stream).build();
    }
}
