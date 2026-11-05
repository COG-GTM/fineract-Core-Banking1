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
package org.apache.fineract.investor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.investor.data.ExternalTransferLoanProductAttributesData;
import org.apache.fineract.investor.domain.ExternalAssetOwnerLoanProductAttributes;
import org.apache.fineract.investor.domain.ExternalAssetOwnerLoanProductAttributesRepository;
import org.apache.fineract.investor.exception.ExternalAssetOwnerLoanProductAttributeInvalidSettlementAttributeException;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class ExternalAssetOwnerLoanProductAttributesCacheKeyTest {

    private static final String SETTLEMENT_MODEL = "SETTLEMENT_MODEL";

    private AnnotationConfigApplicationContext context;
    private ExternalAssetOwnerLoanProductAttributesRepository repository;
    private ExternalAssetOwnerLoanProductAttributesReadService readService;
    private ExternalAssetOwnerLoanProductAttributesWriteService writeService;

    @Configuration
    @EnableCaching
    static class CachingConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        ExternalAssetOwnerLoanProductAttributesRepository repository() {
            return mock(ExternalAssetOwnerLoanProductAttributesRepository.class);
        }

        @Bean
        LoanProductRepository loanProductRepository() {
            LoanProductRepository loanProductRepository = mock(LoanProductRepository.class);
            when(loanProductRepository.existsById(anyLong())).thenReturn(true);
            return loanProductRepository;
        }

        @Bean
        ExternalAssetOwnerLoanProductAttributesMapper mapper() {
            ExternalAssetOwnerLoanProductAttributesMapper mapper = mock(ExternalAssetOwnerLoanProductAttributesMapper.class);
            when(mapper.mapLoanProductAttributes(any())).thenAnswer(invocation -> {
                ExternalAssetOwnerLoanProductAttributes attribute = invocation.getArgument(0);
                ExternalTransferLoanProductAttributesData data = new ExternalTransferLoanProductAttributesData();
                data.setLoanProductId(attribute.getLoanProductId());
                data.setAttributeKey(attribute.getAttributeKey());
                data.setAttributeValue(attribute.getAttributeValue());
                return data;
            });
            return mapper;
        }

        @Bean
        ExternalAssetOwnerLoanProductAttributesReadService readService(ExternalAssetOwnerLoanProductAttributesRepository repository,
                LoanProductRepository loanProductRepository, ExternalAssetOwnerLoanProductAttributesMapper mapper) {
            return new ExternalAssetOwnerLoanProductAttributesReadServiceImpl(repository, loanProductRepository, mapper);
        }

        @Bean
        ExternalAssetOwnerLoanProductAttributesWriteService writeService(ExternalAssetOwnerLoanProductAttributesRepository repository,
                LoanProductRepository loanProductRepository) {
            return new ExternalAssetOwnerLoanProductAttributesWriteServiceImpl(mock(FromJsonHelper.class), repository,
                    loanProductRepository);
        }
    }

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        context = new AnnotationConfigApplicationContext(CachingConfig.class);
        repository = context.getBean(ExternalAssetOwnerLoanProductAttributesRepository.class);
        readService = context.getBean(ExternalAssetOwnerLoanProductAttributesReadService.class);
        writeService = context.getBean(ExternalAssetOwnerLoanProductAttributesWriteService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
        ThreadLocalContextUtil.reset();
    }

    @Test
    void ambiguousProductIdAndAttributeKeyDoNotShareCacheEntry() {
        stubRepositoryWith(attribute(12L, SETTLEMENT_MODEL, "DELAYED_SETTLEMENT"));

        assertThrows(ExternalAssetOwnerLoanProductAttributeInvalidSettlementAttributeException.class,
                () -> readService.retrieveAllLoanProductAttributesByLoanProductId(1L, "2" + SETTLEMENT_MODEL));
        Page<ExternalTransferLoanProductAttributesData> result = readService.retrieveAllLoanProductAttributesByLoanProductId(12L,
                SETTLEMENT_MODEL);

        assertEquals(1, result.getTotalFilteredRecords());
        assertEquals("DELAYED_SETTLEMENT", result.getPageItems().getFirst().getAttributeValue());
    }

    @Test
    void cacheKeySeparatesTenantProductIdAndAttributeKey() {
        stubRepositoryWith();
        readService.retrieveAllLoanProductAttributesByLoanProductId(12L, SETTLEMENT_MODEL);

        Object nativeCache = context.getBean(CacheManager.class).getCache("externalAssetOwnerLoanProductAttributes").getNativeCache();
        assertTrue(((Map<?, ?>) nativeCache).containsKey("default:12:" + SETTLEMENT_MODEL));
    }

    @Test
    void updateEvictsTheEntryReadByDelayedSettlementLookup() {
        stubRepositoryWith(attribute(12L, SETTLEMENT_MODEL, "DEFAULT_SETTLEMENT"));
        assertEquals("DEFAULT_SETTLEMENT", readService.retrieveAllLoanProductAttributesByLoanProductId(12L, SETTLEMENT_MODEL)
                .getPageItems().getFirst().getAttributeValue());
        verify(repository, times(1)).findAll(any(Specification.class), any(Pageable.class));

        ExternalAssetOwnerLoanProductAttributes stored = attribute(12L, SETTLEMENT_MODEL, "DEFAULT_SETTLEMENT");
        when(repository.findById(5L)).thenReturn(Optional.of(stored));
        JsonCommand command = mock(JsonCommand.class);
        when(command.getProductId()).thenReturn(12L);
        when(command.entityId()).thenReturn(5L);
        when(command.json()).thenReturn("{}");
        writeService.updateExternalAssetOwnerLoanProductAttribute(command, SETTLEMENT_MODEL, "DELAYED_SETTLEMENT");

        reset(repository);
        stubRepositoryWith(attribute(12L, SETTLEMENT_MODEL, "DELAYED_SETTLEMENT"));
        assertEquals("DELAYED_SETTLEMENT", readService.retrieveAllLoanProductAttributesByLoanProductId(12L, SETTLEMENT_MODEL)
                .getPageItems().getFirst().getAttributeValue());
        verify(repository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @SuppressWarnings("unchecked")
    private void stubRepositoryWith(ExternalAssetOwnerLoanProductAttributes... attributes) {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(attributes)));
    }

    private static ExternalAssetOwnerLoanProductAttributes attribute(Long loanProductId, String key, String value) {
        ExternalAssetOwnerLoanProductAttributes attribute = new ExternalAssetOwnerLoanProductAttributes();
        attribute.setLoanProductId(loanProductId);
        attribute.setAttributeKey(key);
        attribute.setAttributeValue(value);
        return attribute;
    }
}
