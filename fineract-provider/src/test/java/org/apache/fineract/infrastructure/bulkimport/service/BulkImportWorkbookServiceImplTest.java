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
package org.apache.fineract.infrastructure.bulkimport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import org.apache.fineract.command.core.CommandPipeline;
import org.apache.fineract.infrastructure.bulkimport.data.GlobalEntityType;
import org.apache.fineract.infrastructure.bulkimport.domain.ImportDocumentRepository;
import org.apache.fineract.infrastructure.bulkimport.mapping.ImportDocumentMapper;
import org.apache.fineract.infrastructure.contentstore.util.ContentPipe;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformPayloadTooLargeException;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class BulkImportWorkbookServiceImplTest {

    private static final int MAX_FILE_SIZE = 1024;

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private PlatformSecurityContext securityContext;
    @Mock
    private ImportDocumentRepository importDocumentRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ContentPipe pipe;
    @Mock
    private CommandPipeline commandPipeline;
    @Mock
    private ImportDocumentMapper mapper;
    @Mock
    private AppUser user;

    private final FormDataContentDisposition fileDetail = FormDataContentDisposition.name("file").fileName("users.xls").build();

    private BulkImportWorkbookServiceImpl underTest;

    @BeforeEach
    void setUp() {
        FineractProperties.FineractContentProperties content = new FineractProperties.FineractContentProperties();
        content.setBulkImportMaxFileSize(DataSize.ofBytes(MAX_FILE_SIZE));
        FineractProperties properties = new FineractProperties();
        properties.setContent(content);
        underTest = new BulkImportWorkbookServiceImpl(applicationContext, securityContext, importDocumentRepository, jdbcTemplate, pipe,
                commandPipeline, mapper, properties);
    }

    @Test
    void rejectsUnauthorizedUserBeforeReadingUpload() {
        when(securityContext.authenticatedUser()).thenReturn(user);
        doThrow(new NoAuthorizationException("User has no authority to: IMPORT_USERS")).when(user)
                .validateHasPermissionTo(eq("IMPORT_USERS"), eq(List.of("CREATE_USER")));
        InputStream upload = mock(InputStream.class);

        assertThrows(NoAuthorizationException.class,
                () -> underTest.importWorkbook(GlobalEntityType.USERS.toString(), upload, fileDetail, "en", "dd MMMM yyyy"));

        verifyNoInteractions(upload, importDocumentRepository, commandPipeline, applicationContext);
    }

    @Test
    void rejectsUploadLargerThanConfiguredLimit() {
        when(securityContext.authenticatedUser()).thenReturn(user);
        InputStream upload = new ByteArrayInputStream(new byte[MAX_FILE_SIZE + 1]);

        assertThrows(PlatformPayloadTooLargeException.class,
                () -> underTest.importWorkbook(GlobalEntityType.CLIENTS_PERSON.toString(), upload, fileDetail, "en", "dd MMMM yyyy"));

        verify(user).validateHasPermissionTo(eq("IMPORT_CLIENTS_PERSON"), eq(List.of("CREATE_CLIENT")));
        verifyNoInteractions(importDocumentRepository, commandPipeline, applicationContext);
    }

    @Test
    void rejectsUnknownEntityBeforeReadingUpload() {
        InputStream upload = mock(InputStream.class);

        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> underTest.importWorkbook("unknown", upload, fileDetail, "en", "dd MMMM yyyy"));

        verifyNoInteractions(upload, securityContext, importDocumentRepository);
    }

    @Test
    void acceptsUploadWithinLimitAndChecksFileType() {
        when(securityContext.authenticatedUser()).thenReturn(user);
        InputStream upload = new ByteArrayInputStream(new byte[MAX_FILE_SIZE]);

        GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> underTest.importWorkbook(GlobalEntityType.USERS.toString(), upload, fileDetail, "en", "dd MMMM yyyy"));

        assertEquals("error.msg.invalid.file.extension", exception.getGlobalisationMessageCode());
        verify(user).validateHasPermissionTo(any(), any());
        verifyNoInteractions(importDocumentRepository);
    }
}
