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
package org.apache.fineract.mix.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.command.core.CommandPipeline;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.mix.command.MixTaxonomyMappingUpdateCommand;
import org.apache.fineract.mix.data.MixTaxonomyMappingData;
import org.apache.fineract.mix.data.MixTaxonomyMappingUpdateRequest;
import org.apache.fineract.mix.data.MixTaxonomyMappingUpdateResponse;
import org.apache.fineract.mix.service.MixTaxonomyMappingReadService;
import org.springframework.stereotype.Component;

@Path("/v1/mixmapping")
@Component
@Tag(name = "Mix Mapping", description = "")
@RequiredArgsConstructor
public class MixTaxonomyMappingApiResource {

    // TODO: "UPDATE_XBRLMAPPING" is the legacy permission name; we should rename for consistency
    private static final List<String> READ_PERMISSIONS = List.of("ALL_FUNCTIONS_READ", "READ_MIX_MAPPING", "UPDATE_MIX_MAPPING",
            "UPDATE_XBRLMAPPING");
    private static final List<String> UPDATE_PERMISSIONS = List.of("ALL_FUNCTIONS_WRITE", "UPDATE_MIX_MAPPING", "UPDATE_XBRLMAPPING");

    private final PlatformSecurityContext context;
    private final MixTaxonomyMappingReadService readTaxonomyMappingService;
    private final CommandPipeline commandPipeline;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public MixTaxonomyMappingData retrieveTaxonomyMapping() {
        context.authenticatedUser().validateHasPermissionTo("READ_MIX_MAPPING", READ_PERMISSIONS);

        return this.readTaxonomyMappingService.retrieveTaxonomyMapping();
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public MixTaxonomyMappingUpdateResponse updateTaxonomyMapping(final MixTaxonomyMappingUpdateRequest request) {
        context.authenticatedUser().validateHasPermissionTo("UPDATE_XBRLMAPPING", UPDATE_PERMISSIONS);

        // TODO support multiple configuration file loading; this is the legacy behavior
        if (request.getId() == null) {
            request.setId(1L);
        }

        final var command = new MixTaxonomyMappingUpdateCommand();

        command.setPayload(request);

        final Supplier<MixTaxonomyMappingUpdateResponse> response = commandPipeline.send(command);

        return response.get();
    }
}
