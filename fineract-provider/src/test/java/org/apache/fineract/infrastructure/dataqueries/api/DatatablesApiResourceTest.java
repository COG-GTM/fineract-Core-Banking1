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
package org.apache.fineract.infrastructure.dataqueries.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.service.DatatableReadService;
import org.apache.fineract.infrastructure.dataqueries.service.GenericDataService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatatablesApiResourceTest {

    private static final String DATATABLE_NAME = "loan_collateral_details";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private GenericDataService genericDataService;
    @Mock
    private DatatableReadService datatableReadService;
    @Mock
    private ToApiJsonSerializer<GenericResultsetData> toApiJsonSerializer;
    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @InjectMocks
    private DatatablesApiResource underTest;

    @Test
    void deregisterDatatableIsRoutedThroughCommandFrameworkWithDeregisterPermission() {
        final CommandProcessingResult result = new CommandProcessingResultBuilder().withResourceIdAsString(DATATABLE_NAME).build();
        when(commandsSourceWritePlatformService.logCommandSource(any(CommandWrapper.class))).thenReturn(result);
        when(toApiJsonSerializer.serialize(result)).thenReturn("{\"resourceIdentifier\":\"" + DATATABLE_NAME + "\"}");

        final String response = underTest.deregisterDatatable(DATATABLE_NAME);

        final ArgumentCaptor<CommandWrapper> captor = ArgumentCaptor.forClass(CommandWrapper.class);
        verify(commandsSourceWritePlatformService).logCommandSource(captor.capture());
        final CommandWrapper wrapper = captor.getValue();
        assertEquals("DEREGISTER", wrapper.actionName());
        assertEquals("DATATABLE", wrapper.entityName());
        assertEquals("DEREGISTER_DATATABLE", wrapper.getTaskPermissionName());
        assertEquals("/datatables/deregister/" + DATATABLE_NAME, wrapper.getHref());
        assertNull(wrapper.getEntityId());
        assertTrue(wrapper.isDatatableResource());
        assertTrue(wrapper.isDeregisterDatatable());
        assertEquals("{\"resourceIdentifier\":\"" + DATATABLE_NAME + "\"}", response);
    }

    @Test
    void deregisterDatatablePropagatesAuthorizationFailure() {
        when(commandsSourceWritePlatformService.logCommandSource(any(CommandWrapper.class)))
                .thenThrow(new NoAuthorizationException("User has no authority to: DEREGISTER_DATATABLE"));

        assertThrows(NoAuthorizationException.class, () -> underTest.deregisterDatatable(DATATABLE_NAME));
    }
}
