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
package org.apache.fineract.template.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Map;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.template.domain.Template;
import org.apache.fineract.template.service.TemplateDomainService;
import org.apache.fineract.template.service.TemplateMergeService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemplatesApiResourceTest {

    private static final String SERVER_BASE_URI = "https://localhost:8443/fineract-provider/api/";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private TemplateDomainService templateService;
    @Mock
    private TemplateMergeService templateMergeService;
    @Mock
    private AppUser user;
    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private TemplatesApiResource resource;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void mergeTemplateRequiresReadTemplatePermission() {
        doThrow(new NoAuthorizationException("User has no authority to READ templates")).when(user).validateHasReadPermission("template");

        assertThrows(NoAuthorizationException.class,
                () -> resource.mergeTemplate(3L, uriInfo, "{\"BASE_URI\":\"http://127.0.0.1:1/\"}"));

        verifyNoInteractions(templateService, templateMergeService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergeTemplateDoesNotLetRequestBodyOverrideBaseUri() throws Exception {
        final Template template = mock(Template.class);
        when(templateService.findOneById(anyLong())).thenReturn(template);
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
        when(uriInfo.getBaseUri()).thenReturn(URI.create(SERVER_BASE_URI));
        when(templateMergeService.resolveBaseUri(SERVER_BASE_URI)).thenReturn(SERVER_BASE_URI);

        resource.mergeTemplate(3L, uriInfo, "{\"BASE_URI\":\"http://127.0.0.1:1/\",\"clientId\":1}");

        final ArgumentCaptor<Map<String, Object>> scopes = ArgumentCaptor.forClass(Map.class);
        verify(user).validateHasReadPermission("template");
        verify(templateMergeService).compile(any(Template.class), scopes.capture(), eq(SERVER_BASE_URI));
        assertEquals(SERVER_BASE_URI, scopes.getValue().get("BASE_URI"));
        assertEquals(1, scopes.getValue().get("clientId"));
    }
}
