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
package org.apache.fineract.portfolio.client.adapter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepository;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientImageIdAdapterTest {

    private static final String USER_HIERARCHY = ".2.";

    @Mock
    private ClientRepository repository;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private Client client;

    private ClientImageIdAdapter underTest;

    @BeforeEach
    void setUp() {
        underTest = new ClientImageIdAdapter(repository, context);
        when(context.officeHierarchy()).thenReturn(USER_HIERARCHY);
    }

    @Test
    void validateAccessShouldPassForClientInUserHierarchy() {
        when(repository.fetchByClientIdAndHierarchy(7L, USER_HIERARCHY + "%", USER_HIERARCHY + "%")).thenReturn(client);

        assertDoesNotThrow(() -> underTest.validateAccess(7L));
    }

    @Test
    void validateAccessShouldRejectClientOutsideUserHierarchy() {
        when(repository.fetchByClientIdAndHierarchy(8L, USER_HIERARCHY + "%", USER_HIERARCHY + "%")).thenReturn(null);

        assertThrows(ClientNotFoundException.class, () -> underTest.validateAccess(8L));
    }
}
