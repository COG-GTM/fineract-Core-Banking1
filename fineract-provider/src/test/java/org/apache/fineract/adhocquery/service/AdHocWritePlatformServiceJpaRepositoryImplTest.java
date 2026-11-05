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
package org.apache.fineract.adhocquery.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.adhocquery.domain.AdHoc;
import org.apache.fineract.adhocquery.domain.AdHocRepository;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.exception.SqlValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdHocWritePlatformServiceJpaRepositoryImplTest {

    private static final String STACKED_QUERY = "select id from m_client; insert into m_appuser (username) values ('mallory')";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AdHocRepository adHocRepository;
    @Mock
    private SqlValidator sqlValidator;

    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();
    private AdHocWritePlatformServiceJpaRepositoryImpl service;

    @BeforeEach
    void setUp() {
        service = new AdHocWritePlatformServiceJpaRepositoryImpl(context, adHocRepository, new AdHocDataValidator(fromJsonHelper),
                sqlValidator);
    }

    private JsonCommand command(final String json) {
        return JsonCommand.from(json, fromJsonHelper.parse(json), fromJsonHelper, "ADHOC", 1L, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    @Test
    void updateRejectsQueryThatFailsSqlValidation() {
        doThrow(new SqlValidationException("invalid SQL statement (detected 'inject-stacked-query' pattern)")).when(sqlValidator)
                .validate("adhoc", STACKED_QUERY);

        assertThrows(SqlValidationException.class, () -> service.updateAdHocQuery(1L, command("{\"query\":\"" + STACKED_QUERY + "\"}")));

        verify(adHocRepository, never()).findById(any());
        verify(adHocRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsInvalidTableNameBeforeTouchingRepository() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> service.updateAdHocQuery(1L, command("{\"tableName\":\"m_appuser (username) select 'x'; --\"}")));

        verify(adHocRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateValidatesChangedQueryWithAdhocProfile() {
        final String query = "select id, display_name from m_client";
        when(adHocRepository.findById(1L)).thenReturn(Optional.of(new AdHoc()));

        service.updateAdHocQuery(1L, command("{\"query\":\"" + query + "\"}"));

        verify(sqlValidator).validate("adhoc", query);
        verify(adHocRepository).saveAndFlush(any(AdHoc.class));
    }

    @Test
    void updateWithoutQuerySkipsSqlValidation() {
        when(adHocRepository.findById(1L)).thenReturn(Optional.of(new AdHoc()));

        service.updateAdHocQuery(1L, command("{\"name\":\"renamed\"}"));

        verify(sqlValidator, never()).validate(anyString(), anyString());
    }
}
