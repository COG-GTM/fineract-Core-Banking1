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
package org.apache.fineract.mix.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.mix.data.MixTaxonomyMappingUpdateRequest;
import org.apache.fineract.mix.domain.MixTaxonomyMapping;
import org.apache.fineract.mix.domain.MixTaxonomyMappingRepository;
import org.apache.fineract.mix.mapping.MixTaxonomyMappingUpdateRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MixTaxonomyMappingWriteServiceImplTest {

    private MixTaxonomyMappingRepository repository;
    private MixTaxonomyMappingUpdateRequestMapper mapper;
    private MixTaxonomyMappingWriteServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(MixTaxonomyMappingRepository.class);
        mapper = mock(MixTaxonomyMappingUpdateRequestMapper.class);
        when(mapper.map(any())).thenReturn(new MixTaxonomyMapping());
        service = new MixTaxonomyMappingWriteServiceImpl(repository, mapper);
    }

    @Test
    void savesArithmeticMapping() {
        assertThat(service.updateMapping(request("{\"1\":\"{1001}+{1002}*2\",\"2\":\"-({3000})\"}"))).isNotNull();

        verify(repository).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"1\":\"Java.type('java.lang.Runtime').getRuntime().exec('id')\"}", "{\"1\":\"{1001}+abc\"}",
            "{\"1\":5}", "{\"1\":[\"{1001}\"]}", "[\"{1001}\"]", "not json" })
    void rejectsNonArithmeticMapping(final String config) {
        assertThatThrownBy(() -> service.updateMapping(request(config))).isInstanceOf(PlatformApiDataValidationException.class);

        verify(repository, never()).save(any());
    }

    private static MixTaxonomyMappingUpdateRequest request(final String config) {
        return MixTaxonomyMappingUpdateRequest.builder().id(1L).identifier("default").config(config).currency("USD").build();
    }
}
