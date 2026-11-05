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
package org.apache.fineract.infrastructure.survey.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.dataqueries.api.DataTableApiConstant;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.exception.DatatableNotFoundException;
import org.apache.fineract.infrastructure.dataqueries.service.DatatableReadService;
import org.apache.fineract.infrastructure.dataqueries.service.GenericDataService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ReadSurveyServiceImplTest {

    private static final Long CLIENT_ID = 7L;
    private static final Long ENTRY_ID = 1L;

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SqlValidator sqlValidator;
    @Mock
    private GenericDataService genericDataService;
    @Mock
    private DatatableReadService datatableReadService;

    private ReadSurveyServiceImpl underTest;

    @BeforeEach
    void setUp() {
        underTest = new ReadSurveyServiceImpl(context, jdbcTemplate, sqlValidator, genericDataService, datatableReadService);
    }

    @Test
    void retrieveSurveyEntryRejectsNonSurveyDatatable() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("client_kyc_details"), eq(DataTableApiConstant.CATEGORY_PPI)))
                .thenReturn(0L);

        assertThrows(DatatableNotFoundException.class, () -> underTest.retrieveSurveyEntry("client_kyc_details", CLIENT_ID, ENTRY_ID));

        verifyNoInteractions(datatableReadService);
    }

    @Test
    void retrieveSurveyEntryReadsRegisteredSurveyTable() {
        final GenericResultsetData results = new GenericResultsetData(null, null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("ppi_kenya_2009"), eq(DataTableApiConstant.CATEGORY_PPI)))
                .thenReturn(1L);
        when(datatableReadService.retrieveDataTableGenericResultSet("ppi_kenya_2009", CLIENT_ID, null, ENTRY_ID)).thenReturn(results);

        assertSame(results, underTest.retrieveSurveyEntry("ppi_kenya_2009", CLIENT_ID, ENTRY_ID));
    }
}
