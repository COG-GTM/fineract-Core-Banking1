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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.mix.data.MixTaxonomyMappingUpdateRequest;
import org.apache.fineract.mix.data.MixTaxonomyMappingUpdateResponse;
import org.apache.fineract.mix.domain.MixTaxonomyMappingRepository;
import org.apache.fineract.mix.mapping.MixTaxonomyMappingUpdateRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class MixTaxonomyMappingWriteServiceImpl implements MixTaxonomyMappingWriteService {

    private final MixTaxonomyMappingRepository repository;
    private final MixTaxonomyMappingUpdateRequestMapper mapper;

    @Transactional
    @Override
    public MixTaxonomyMappingUpdateResponse updateMapping(@Valid final MixTaxonomyMappingUpdateRequest request) {
        validateConfig(request.getConfig());

        final var taxonomyMapping = mapper.map(request);

        repository.save(taxonomyMapping);

        return MixTaxonomyMappingUpdateResponse.builder().entityId(taxonomyMapping.getId()).build();
    }

    private static void validateConfig(final String config) {
        if (config == null) {
            return;
        }
        final JsonObject configObject;
        try {
            final JsonElement element = JsonParser.parseString(config);
            if (!element.isJsonObject()) {
                throw invalidConfig("error.msg.mix.mapping.config.invalid",
                        "Mapping config must be a JSON object of taxonomy id to formula");
            }
            configObject = element.getAsJsonObject();
        } catch (final JsonParseException e) {
            throw new PlatformApiDataValidationException("error.msg.mix.mapping.config.invalid", "Mapping config must be valid JSON",
                    "config", e);
        }
        for (final Map.Entry<String, JsonElement> entry : configObject.entrySet()) {
            final JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw invalidConfig("error.msg.mix.mapping.formula.invalid",
                        "Mapping formula for taxonomy " + entry.getKey() + " must be a string");
            }
            try {
                MixTaxonomyMappingFormulaEvaluator.validate(value.getAsString());
            } catch (final IllegalArgumentException e) {
                throw invalidConfig("error.msg.mix.mapping.formula.invalid",
                        "Invalid mapping formula for taxonomy " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }

    private static PlatformApiDataValidationException invalidConfig(final String messageCode, final String message) {
        return new PlatformApiDataValidationException(messageCode, message, "config");
    }
}
