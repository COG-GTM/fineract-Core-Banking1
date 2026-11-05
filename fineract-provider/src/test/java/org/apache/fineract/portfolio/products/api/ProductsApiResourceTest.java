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
package org.apache.fineract.portfolio.products.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import java.util.Set;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.exception.ResourceNotFoundException;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.products.data.ProductData;
import org.apache.fineract.portfolio.products.service.ShareProductReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class ProductsApiResourceTest {

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;
    @Mock
    private DefaultToApiJsonSerializer<ProductData> toApiJsonSerializer;
    @Mock
    private PlatformSecurityContext platformSecurityContext;
    @Mock
    private AppUser appUser;
    @Mock
    private ShareProductReadPlatformService shareProductReadPlatformService;
    @Mock
    private UriInfo uriInfo;

    @InjectMocks
    private ProductsApiResource productsApiResource;

    @Test
    void unsupportedProductTypeIsRejectedBeforeAnyBeanLookup() {
        assertThrows(ResourceNotFoundException.class, () -> productsApiResource.retrieveTemplate("client", uriInfo));
        assertThrows(ResourceNotFoundException.class, () -> productsApiResource.retrieveProduct(1L, "client", uriInfo));
        assertThrows(ResourceNotFoundException.class, () -> productsApiResource.retrieveAllProducts("jdbcTemplate", 0, 10, uriInfo));
        assertThrows(ResourceNotFoundException.class, () -> productsApiResource.retrieveAllProducts("Share", 0, 10, uriInfo));
        assertThrows(ResourceNotFoundException.class, () -> productsApiResource.retrieveAllProducts(null, 0, 10, uriInfo));

        verifyNoInteractions(applicationContext, platformSecurityContext);
    }

    @Test
    void readsRequireReadShareProductPermission() {
        when(platformSecurityContext.authenticatedUser()).thenReturn(appUser);
        doThrow(new NoAuthorizationException("User has no authority to READ shareproducts")).when(appUser)
                .validateHasReadPermission("SHAREPRODUCT");

        assertThrows(NoAuthorizationException.class, () -> productsApiResource.retrieveTemplate("share", uriInfo));
        assertThrows(NoAuthorizationException.class, () -> productsApiResource.retrieveProduct(1L, "share", uriInfo));
        assertThrows(NoAuthorizationException.class, () -> productsApiResource.retrieveAllProducts("share", 0, 10, uriInfo));

        verify(applicationContext, never()).getBean(anyString(), eq(ShareProductReadPlatformService.class));
        verifyNoInteractions(shareProductReadPlatformService);
    }

    @Test
    void authorizedUserCanListShareProducts() {
        @SuppressWarnings("unchecked")
        Page<ProductData> page = mock(Page.class);
        ApiRequestJsonSerializationSettings settings = mock(ApiRequestJsonSerializationSettings.class);
        when(platformSecurityContext.authenticatedUser()).thenReturn(appUser);
        when(applicationContext.getBean("shareReadPlatformService", ShareProductReadPlatformService.class))
                .thenReturn(shareProductReadPlatformService);
        when(shareProductReadPlatformService.retrieveAllProducts(0, 10)).thenReturn(page);
        when(shareProductReadPlatformService.getResponseDataParams()).thenReturn(Set.of());
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
        when(apiRequestParameterHelper.process(any())).thenReturn(settings);
        when(toApiJsonSerializer.serialize(eq(settings), eq(page), any())).thenReturn("{\"totalFilteredRecords\":0}");

        assertEquals("{\"totalFilteredRecords\":0}", productsApiResource.retrieveAllProducts("share", 0, 10, uriInfo));
        verify(appUser).validateHasReadPermission("SHAREPRODUCT");
    }

    @Test
    void selfServiceShareProductReadsDoNotRequireReadShareProductPermission() {
        @SuppressWarnings("unchecked")
        Page<ProductData> page = mock(Page.class);
        ProductData product = mock(ProductData.class);
        ApiRequestJsonSerializationSettings settings = mock(ApiRequestJsonSerializationSettings.class);
        when(applicationContext.getBean("shareReadPlatformService", ShareProductReadPlatformService.class))
                .thenReturn(shareProductReadPlatformService);
        when(shareProductReadPlatformService.retrieveAllProducts(0, 10)).thenReturn(page);
        when(shareProductReadPlatformService.retrieveOne(1L, false)).thenReturn(product);
        when(shareProductReadPlatformService.getResponseDataParams()).thenReturn(Set.of());
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
        when(apiRequestParameterHelper.process(any())).thenReturn(settings);
        when(toApiJsonSerializer.serialize(eq(settings), eq(page), any())).thenReturn("{\"totalFilteredRecords\":0}");
        when(toApiJsonSerializer.serialize(eq(settings), eq(product), any())).thenReturn("{\"id\":1}");

        assertEquals("{\"totalFilteredRecords\":0}", productsApiResource.retrieveAllShareProductsForSelfService(0, 10, uriInfo));
        assertEquals("{\"id\":1}", productsApiResource.retrieveShareProductForSelfService(1L, uriInfo));
        verifyNoInteractions(platformSecurityContext);
    }
}
