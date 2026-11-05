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
package org.apache.fineract.infrastructure.core.exceptionmapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.ws.rs.core.Response;
import org.apache.fineract.command.core.Command;
import org.apache.fineract.command.core.exception.CommandIllegalArgumentException;
import org.apache.fineract.infrastructure.core.data.ApiGlobalErrorResponse;
import org.junit.jupiter.api.Test;

class CommandIllegalArgumentExceptionMapperTest {

    @Test
    void mapsToBadRequest() {
        var mapper = new CommandIllegalArgumentExceptionMapper();
        var response = mapper.toResponse(new CommandIllegalArgumentException(new Command<>(), "idempotency key too long"));

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        var body = (ApiGlobalErrorResponse) response.getEntity();
        assertEquals("error.msg.command.illegal.argument", body.getUserMessageGlobalisationCode());
        assertEquals("Illegal argument for Command command: idempotency key too long", body.getDefaultUserMessage());
    }
}
