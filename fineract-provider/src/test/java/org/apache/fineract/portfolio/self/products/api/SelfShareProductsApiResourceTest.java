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
package org.apache.fineract.portfolio.self.products.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.UriInfo;
import org.apache.fineract.portfolio.products.api.ProductsApiResource;
import org.apache.fineract.portfolio.self.client.service.AppuserClientMapperReadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SelfShareProductsApiResourceTest {

    @Mock
    private ProductsApiResource productsApiResource;
    @Mock
    private AppuserClientMapperReadService appUserClientMapperReadService;
    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private SelfShareProductsApiResource selfShareProductsApiResource;

    @Test
    void mappedClientReadsShareProductsThroughSelfServicePath() {
        when(productsApiResource.retrieveAllShareProductsForSelfService(0, 10, uriInfo)).thenReturn("list");
        when(productsApiResource.retrieveShareProductForSelfService(1L, uriInfo)).thenReturn("one");

        assertEquals("list", selfShareProductsApiResource.retrieveAllProducts(42L, 0, 10, uriInfo));
        assertEquals("one", selfShareProductsApiResource.retrieveProduct(42L, 1L, null, uriInfo));

        InOrder order = inOrder(appUserClientMapperReadService, productsApiResource);
        order.verify(appUserClientMapperReadService).validateAppuserClientsMapping(42L);
        order.verify(productsApiResource).retrieveAllShareProductsForSelfService(0, 10, uriInfo);
        order.verify(appUserClientMapperReadService).validateAppuserClientsMapping(42L);
        order.verify(productsApiResource).retrieveShareProductForSelfService(1L, uriInfo);
    }

    @Test
    void unmappedClientIsRejectedBeforeAnyProductRead() {
        doThrow(new IllegalStateException("not mapped")).when(appUserClientMapperReadService).validateAppuserClientsMapping(7L);

        assertThrows(IllegalStateException.class, () -> selfShareProductsApiResource.retrieveAllProducts(7L, 0, 10, uriInfo));
        assertThrows(IllegalStateException.class, () -> selfShareProductsApiResource.retrieveProduct(7L, 1L, null, uriInfo));

        verifyNoInteractions(productsApiResource);
    }
}
