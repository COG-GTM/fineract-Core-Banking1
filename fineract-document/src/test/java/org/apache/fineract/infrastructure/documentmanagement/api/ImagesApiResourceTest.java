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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayInputStream;
import org.apache.fineract.command.core.CommandPipeline;
import org.apache.fineract.infrastructure.contentstore.processor.Base64DecoderContentProcessor;
import org.apache.fineract.infrastructure.contentstore.processor.Base64EncoderContentProcessor;
import org.apache.fineract.infrastructure.contentstore.processor.DataUrlDecoderContentProcessor;
import org.apache.fineract.infrastructure.contentstore.processor.DataUrlEncoderContentProcessor;
import org.apache.fineract.infrastructure.contentstore.processor.ImageResizeContentProcessor;
import org.apache.fineract.infrastructure.documentmanagement.exception.DocumentNotFoundException;
import org.apache.fineract.infrastructure.documentmanagement.service.ImageReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImagesApiResourceTest {

    private static final String ENTITY_TYPE = "clients";
    private static final Long ENTITY_ID = 4711L;

    @Mock
    private ImageReadPlatformService imageReadPlatformService;
    @Mock
    private CommandPipeline commandPipeline;
    @Mock
    private ImageResizeContentProcessor imageResizeContentProcessor;
    @Mock
    private Base64EncoderContentProcessor base64EncoderContentProcessor;
    @Mock
    private DataUrlEncoderContentProcessor dataUrlEncoderContentProcessor;
    @Mock
    private Base64DecoderContentProcessor base64DecoderContentProcessor;
    @Mock
    private DataUrlDecoderContentProcessor dataUrlDecoderContentProcessor;
    @Mock
    private FileUploadValidator fileUploadValidator;

    private ImagesApiResource underTest;

    @BeforeEach
    void setUp() {
        underTest = new ImagesApiResource(imageReadPlatformService, commandPipeline, imageResizeContentProcessor,
                base64EncoderContentProcessor, dataUrlEncoderContentProcessor, base64DecoderContentProcessor,
                dataUrlDecoderContentProcessor, fileUploadValidator);
        lenient().doThrow(new DocumentNotFoundException(ENTITY_TYPE, ENTITY_ID, -1L)).when(imageReadPlatformService)
                .validateAccess(ENTITY_TYPE, ENTITY_ID);
    }

    @Test
    void createMultipartImageShouldRejectEntityOutsideUserHierarchy() {
        assertThrows(DocumentNotFoundException.class,
                () -> underTest.createImage(ENTITY_TYPE, ENTITY_ID, 3L, new ByteArrayInputStream(new byte[] { 1, 2, 3 }), null, null));

        verifyNoInteractions(fileUploadValidator, commandPipeline);
    }

    @Test
    void createDataUrlImageShouldRejectEntityOutsideUserHierarchy() {
        assertThrows(DocumentNotFoundException.class,
                () -> underTest.createImage(ENTITY_TYPE, ENTITY_ID, new ByteArrayInputStream(new byte[] { 1, 2, 3 })));

        verifyNoInteractions(dataUrlDecoderContentProcessor, commandPipeline);
    }

    @Test
    void updateImageShouldRejectEntityOutsideUserHierarchy() {
        assertThrows(DocumentNotFoundException.class,
                () -> underTest.updateImage(ENTITY_TYPE, ENTITY_ID, new ByteArrayInputStream(new byte[] { 1, 2, 3 })));

        verifyNoInteractions(commandPipeline);
    }

    @Test
    void deleteImageShouldRejectEntityOutsideUserHierarchy() {
        assertThrows(DocumentNotFoundException.class, () -> underTest.deleteImage(ENTITY_TYPE, ENTITY_ID));

        verify(commandPipeline, never()).send(any());
    }

    @Test
    void retrieveImageShouldRejectEntityOutsideUserHierarchy() {
        assertThrows(DocumentNotFoundException.class, () -> underTest.retrieveImage(ENTITY_TYPE, ENTITY_ID, null, null, null, null));

        verify(imageReadPlatformService, never()).retrieveImage(ENTITY_TYPE, ENTITY_ID);
    }
}
