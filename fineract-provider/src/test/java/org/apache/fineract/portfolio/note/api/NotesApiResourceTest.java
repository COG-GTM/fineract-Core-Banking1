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
package org.apache.fineract.portfolio.note.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;
import org.apache.fineract.command.core.CommandPipeline;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.note.data.NoteData;
import org.apache.fineract.portfolio.note.data.NoteDeleteResponse;
import org.apache.fineract.portfolio.note.data.NoteUpdateRequest;
import org.apache.fineract.portfolio.note.domain.NoteType;
import org.apache.fineract.portfolio.note.service.NoteReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotesApiResourceTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private NoteReadPlatformService readPlatformService;
    @Mock
    private CommandPipeline commandPipeline;
    @Mock
    private AppUser user;

    private NotesApiResource resource;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
        resource = new NotesApiResource(context, readPlatformService, commandPipeline);
    }

    @Test
    void retrieveNoteRequiresReadPermissionForNoteType() {
        doThrow(new NoAuthorizationException("no")).when(user).validateHasReadPermission("LOANNOTE");

        assertThrows(NoAuthorizationException.class, () -> resource.retrieveNote("loans", 41L, 900L));

        verifyNoInteractions(readPlatformService);
    }

    @Test
    void retrieveNotesByResourceRequiresReadPermissionForNoteType() {
        doThrow(new NoAuthorizationException("no")).when(user).validateHasReadPermission("CLIENTNOTE");

        assertThrows(NoAuthorizationException.class, () -> resource.retrieveNotesByResource("clients", 7L));

        verifyNoInteractions(readPlatformService);
    }

    @Test
    void retrieveNoteReturnsNoteWhenPermitted() {
        final NoteData note = new NoteData();
        when(readPlatformService.retrieveNote(76L, 1L, NoteType.CLIENT.getValue())).thenReturn(note);

        assertEquals(note, resource.retrieveNote("clients", 1L, 76L));

        verify(user).validateHasReadPermission("CLIENTNOTE");
    }

    @Test
    void updateNoteRequiresUpdatePermissionForNoteType() {
        doThrow(new NoAuthorizationException("no")).when(user).validateHasPermissionTo("UPDATE_LOANNOTE",
                List.of("ALL_FUNCTIONS", "ALL_FUNCTIONS_WRITE", "UPDATE_LOANNOTE"));

        assertThrows(NoAuthorizationException.class, () -> resource.updateNote("loans", 41L, 900L, new NoteUpdateRequest()));

        verify(commandPipeline, never()).send(any());
    }

    @Test
    void deleteNoteRequiresDeletePermissionForNoteType() {
        doThrow(new NoAuthorizationException("no")).when(user).validateHasPermissionTo("DELETE_CLIENTNOTE",
                List.of("ALL_FUNCTIONS", "ALL_FUNCTIONS_WRITE", "DELETE_CLIENTNOTE"));

        assertThrows(NoAuthorizationException.class, () -> resource.deleteNote("clients", 7L, 120L));

        verify(commandPipeline, never()).send(any());
    }

    @Test
    void deleteNoteSendsCommandWhenPermitted() {
        final NoteDeleteResponse expected = NoteDeleteResponse.builder().resourceId(120L).build();
        when(commandPipeline.send(any())).thenReturn((Supplier<Object>) () -> expected);

        assertEquals(expected, resource.deleteNote("groups", 3L, 120L));

        verify(user).validateHasPermissionTo("DELETE_GROUPNOTE", List.of("ALL_FUNCTIONS", "ALL_FUNCTIONS_WRITE", "DELETE_GROUPNOTE"));
    }

    @Test
    void savingsTransactionNotesRequireAllFunctions() {
        doThrow(new NoAuthorizationException("no")).when(user).validateHasPermissionTo(anyString(), anyList());

        assertThrows(NoAuthorizationException.class, () -> resource.deleteNote("savingsTransactions", 5L, 10L));

        verify(user).validateHasPermissionTo("DELETE_INVALIDNOTE", List.of("ALL_FUNCTIONS", "ALL_FUNCTIONS_WRITE", "DELETE_INVALIDNOTE"));
        verify(commandPipeline, never()).send(any());
    }

    @ParameterizedTest
    @CsvSource({ "CLIENT,CLIENTNOTE", "LOAN,LOANNOTE", "LOAN_TRANSACTION,LOANTRANSACTIONNOTE", "SAVING_ACCOUNT,SAVINGNOTE",
            "GROUP,GROUPNOTE", "SHARE_ACCOUNT,INVALIDNOTE", "SAVINGS_TRANSACTION,INVALIDNOTE" })
    void permissionEntityNameMatchesSeededPermissions(final NoteType type, final String expected) {
        assertEquals(expected, NotesApiResource.permissionEntityName(type));
    }

    @Test
    void securityConfigNotePatternsCoverSingleNotePaths() {
        final var matcher = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.DELETE, "/api/*/clients/*/notes/**");

        assertTrue(matcher.matches(request("DELETE", "/api/v1/clients/7/notes/120")));
        assertTrue(matcher.matches(request("DELETE", "/api/v1/clients/7/notes")));
        assertFalse(matcher.matches(request("DELETE", "/api/v1/clients/7")));
    }

    private static MockHttpServletRequest request(final String method, final String uri) {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServletPath(uri);
        return request;
    }
}
