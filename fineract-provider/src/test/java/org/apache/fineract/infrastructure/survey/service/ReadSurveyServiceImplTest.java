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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.dataqueries.data.EntityTables;
import org.apache.fineract.infrastructure.dataqueries.exception.DatatableNotFoundException;
import org.apache.fineract.infrastructure.dataqueries.service.DatatableReadService;
import org.apache.fineract.infrastructure.dataqueries.service.DatatableUtil;
import org.apache.fineract.infrastructure.dataqueries.service.GenericDataService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.apache.fineract.infrastructure.survey.data.ClientScoresOverview;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadSurveyServiceImplTest {

    private static final Long CLIENT_ID = 4821L;
    private static final String SURVEY_NAME = "ppi_kenya_2009";

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
    @Mock
    private DatatableUtil datatableUtil;

    @InjectMocks
    private ReadSurveyServiceImpl underTest;

    @BeforeEach
    void setUp() {
        AppUser user = Mockito.mock(AppUser.class);
        when(user.getId()).thenReturn(7L);
        when(context.authenticatedUser()).thenReturn(user);
    }

    @Test
    void overviewForClientOutsideOfficeHierarchyIsRejectedBeforeAnyScoreQuery() {
        when(datatableUtil.checkMainResourceExistsWithinScope(EntityTables.CLIENT, CLIENT_ID))
                .thenThrow(new DatatableNotFoundException(EntityTables.CLIENT, CLIENT_ID));

        assertThrows(DatatableNotFoundException.class, () -> underTest.retrieveClientSurveyScoreOverview(CLIENT_ID));

        verify(jdbcTemplate, never()).queryForRowSet(anyString());
        verify(jdbcTemplate, never()).queryForRowSet(anyString(), anyLong());
    }

    @Test
    void namedOverviewForClientOutsideOfficeHierarchyIsRejectedBeforeAnyScoreQuery() {
        when(datatableUtil.checkMainResourceExistsWithinScope(EntityTables.CLIENT, CLIENT_ID))
                .thenThrow(new DatatableNotFoundException(EntityTables.CLIENT, CLIENT_ID));

        assertThrows(DatatableNotFoundException.class, () -> underTest.retrieveClientSurveyScoreOverview(SURVEY_NAME, CLIENT_ID));

        verify(jdbcTemplate, never()).queryForRowSet(anyString(), any(Object[].class));
    }

    @Test
    void overviewForClientWithinOfficeHierarchyReturnsScores() {
        SqlRowSet surveyNames = Mockito.mock(SqlRowSet.class);
        when(surveyNames.next()).thenReturn(true, false);
        when(surveyNames.getString("name")).thenReturn(SURVEY_NAME);
        when(jdbcTemplate.queryForRowSet(anyString())).thenReturn(surveyNames);

        SqlRowSet scores = Mockito.mock(SqlRowSet.class);
        when(scores.next()).thenReturn(true, false);
        when(scores.getString("surveyName")).thenReturn(SURVEY_NAME);
        when(scores.getLong("score")).thenReturn(42L);
        when(scores.getLong("id")).thenReturn(1L);
        when(scores.getDate("date")).thenReturn(Date.valueOf(LocalDate.of(2026, 1, 1)));
        when(jdbcTemplate.queryForRowSet(anyString(), anyLong())).thenReturn(scores);

        List<ClientScoresOverview> result = underTest.retrieveClientSurveyScoreOverview(CLIENT_ID);

        assertEquals(1, result.size());
        InOrder order = inOrder(datatableUtil, jdbcTemplate);
        order.verify(datatableUtil).checkMainResourceExistsWithinScope(EntityTables.CLIENT, CLIENT_ID);
        order.verify(jdbcTemplate).queryForRowSet(anyString(), anyLong());
    }
}
