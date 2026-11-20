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
package org.apache.fineract.portfolio.delinquency.service;

import static java.time.Month.JANUARY;
import static org.apache.fineract.portfolio.delinquency.domain.DelinquencyAction.PAUSE;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.portfolio.delinquency.data.DelinquencyRangeData;
import org.apache.fineract.portfolio.delinquency.domain.DelinquencyBucketRepository;
import org.apache.fineract.portfolio.delinquency.domain.DelinquencyRange;
import org.apache.fineract.portfolio.delinquency.domain.DelinquencyRangeRepository;
import org.apache.fineract.portfolio.delinquency.domain.LoanDelinquencyAction;
import org.apache.fineract.portfolio.delinquency.domain.LoanDelinquencyActionRepository;
import org.apache.fineract.portfolio.delinquency.domain.LoanDelinquencyTagHistoryRepository;
import org.apache.fineract.portfolio.delinquency.domain.LoanInstallmentDelinquencyTagRepository;
import org.apache.fineract.portfolio.delinquency.mapper.DelinquencyBucketMapper;
import org.apache.fineract.portfolio.delinquency.mapper.DelinquencyRangeMapper;
import org.apache.fineract.portfolio.delinquency.mapper.LoanDelinquencyTagMapper;
import org.apache.fineract.portfolio.delinquency.validator.LoanDelinquencyActionData;
import org.apache.fineract.portfolio.loanaccount.data.CollectionData;
import org.apache.fineract.portfolio.loanaccount.data.DelinquencyPausePeriod;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DelinquencyReadPlatformServiceImplTest {

    @Mock
    private DelinquencyRangeRepository repositoryRange;

    @Mock
    private DelinquencyBucketRepository repositoryBucket;
    @Mock
    private LoanDelinquencyTagHistoryRepository repositoryLoanDelinquencyTagHistory;
    @Mock
    private DelinquencyRangeMapper mapperRange;
    @Mock
    private DelinquencyBucketMapper mapperBucket;

    @Mock
    private LoanDelinquencyTagMapper mapperLoanDelinquencyTagHistory;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private LoanDelinquencyDomainService loanDelinquencyDomainService;

    @Mock
    private LoanInstallmentDelinquencyTagRepository repositoryLoanInstallmentDelinquencyTag;

    @Mock
    private LoanDelinquencyActionRepository loanDelinquencyActionRepository;

    @InjectMocks
    private DelinquencyReadPlatformServiceImpl underTest;

    @Test
    public void testNoEnrichmentWhenThereIsNoDelinquencyAction() {
        // given
        CollectionData collectionData = CollectionData.template();
        Collection<LoanDelinquencyActionData> delinquencyActions = List.of();

        // when
        underTest.enrichWithDelinquencyPausePeriodInfo(collectionData, delinquencyActions, LocalDate.of(2023, JANUARY, 12));

        Assertions.assertTrue(collectionData.getDelinquencyPausePeriods().isEmpty());
    }

    @Test
    public void testMultiplePausesWithoutResumeActionCurrentlyInPauseFirstDay() {
        // given
        CollectionData collectionData = CollectionData.template();
        Collection<LoanDelinquencyActionData> delinquencyActions = List.of(
                new LoanDelinquencyActionData(
                        new LoanDelinquencyAction(null, PAUSE, LocalDate.of(2023, JANUARY, 10), LocalDate.of(2023, JANUARY, 11))),
                new LoanDelinquencyActionData(
                        new LoanDelinquencyAction(null, PAUSE, LocalDate.of(2023, JANUARY, 12), LocalDate.of(2023, JANUARY, 13))),
                new LoanDelinquencyActionData(
                        new LoanDelinquencyAction(null, PAUSE, LocalDate.of(2023, JANUARY, 15), LocalDate.of(2023, JANUARY, 20))));

        // when
        underTest.enrichWithDelinquencyPausePeriodInfo(collectionData, delinquencyActions, LocalDate.of(2023, JANUARY, 12));

        // then
        verifyPausePeriods(collectionData, //
                pausePeriod(false, "2023-01-10", "2023-01-11"), //
                pausePeriod(true, "2023-01-12", "2023-01-13"), //
                pausePeriod(false, "2023-01-15", "2023-01-20") //
        );
    }

    @Test
    public void testMultiplePausesWithoutResumeActionCurrentlyInPauseLastDay() {
        // given
        CollectionData collectionData = CollectionData.template();
        Collection<LoanDelinquencyActionData> delinquencyActions = List.of(
                new LoanDelinquencyActionData(
                        new LoanDelinquencyAction(null, PAUSE, LocalDate.of(2023, JANUARY, 10), LocalDate.of(2023, JANUARY, 11))),
                new LoanDelinquencyActionData(
                        new LoanDelinquencyAction(null, PAUSE, LocalDate.of(2023, JANUARY, 12), LocalDate.of(2023, JANUARY, 13))),
                new LoanDelinquencyActionData(
                        new LoanDelinquencyAction(null, PAUSE, LocalDate.of(2023, JANUARY, 15), LocalDate.of(2023, JANUARY, 20))));

        // when
        underTest.enrichWithDelinquencyPausePeriodInfo(collectionData, delinquencyActions, LocalDate.of(2023, JANUARY, 13));

        verifyPausePeriods(collectionData, //
                pausePeriod(false, "2023-01-10", "2023-01-11"), //
                pausePeriod(true, "2023-01-12", "2023-01-13"), //
                pausePeriod(false, "2023-01-15", "2023-01-20") //
        );
    }

    @Test
    public void testMultiplePausesWithoutResumeActionCurrentBusinessDateBetweenStartAndEndDate() {
        // given
        CollectionData collectionData = CollectionData.template();
        Collection<LoanDelinquencyActionData> delinquencyActions = List.of(
                new LoanDelinquencyActionData(
                        new LoanDelinquencyAction(null, PAUSE, LocalDate.of(2023, JANUARY, 10), LocalDate.of(2023, JANUARY, 11))),
                new LoanDelinquencyActionData(
                        new LoanDelinquencyAction(null, PAUSE, LocalDate.of(2023, JANUARY, 12), LocalDate.of(2023, JANUARY, 14))),
                new LoanDelinquencyActionData(
                        new LoanDelinquencyAction(null, PAUSE, LocalDate.of(2023, JANUARY, 15), LocalDate.of(2023, JANUARY, 20))));

        // when
        underTest.enrichWithDelinquencyPausePeriodInfo(collectionData, delinquencyActions, LocalDate.of(2023, JANUARY, 13));

        verifyPausePeriods(collectionData, //
                pausePeriod(false, "2023-01-10", "2023-01-11"), //
                pausePeriod(true, "2023-01-12", "2023-01-14"), //
                pausePeriod(false, "2023-01-15", "2023-01-20") //
        );
    }

    @Test
    public void testMultiplePausesWithoutResumeCurrentBusinessDateIsNotOverlappingWithAnyOfThePauses() {
        // given
        CollectionData collectionData = CollectionData.template();
        Collection<LoanDelinquencyActionData> delinquencyActions = List.of(
                new LoanDelinquencyActionData(
                        new LoanDelinquencyAction(null, PAUSE, LocalDate.of(2023, JANUARY, 10), LocalDate.of(2023, JANUARY, 11))),
                new LoanDelinquencyActionData(
                        new LoanDelinquencyAction(null, PAUSE, LocalDate.of(2023, JANUARY, 13), LocalDate.of(2023, JANUARY, 14))),
                new LoanDelinquencyActionData(
                        new LoanDelinquencyAction(null, PAUSE, LocalDate.of(2023, JANUARY, 15), LocalDate.of(2023, JANUARY, 20))));

        // when
        underTest.enrichWithDelinquencyPausePeriodInfo(collectionData, delinquencyActions, LocalDate.of(2023, JANUARY, 12));

        // then
        verifyPausePeriods(collectionData, //
                pausePeriod(false, "2023-01-10", "2023-01-11"), //
                pausePeriod(false, "2023-01-13", "2023-01-14"), //
                pausePeriod(false, "2023-01-15", "2023-01-20") //
        );
    }

    private void verifyPausePeriods(CollectionData collectionData, DelinquencyPausePeriod... pausePeriods) {
        if (pausePeriods.length > 0) {
            Assertions.assertEquals(Arrays.asList(pausePeriods), collectionData.getDelinquencyPausePeriods());
        } else {
            Assertions.assertNull(collectionData.getDelinquencyPausePeriods());
        }
    }

    private DelinquencyPausePeriod pausePeriod(boolean active, String startDate, String endDate) {
        return new DelinquencyPausePeriod(active, LocalDate.parse(startDate), LocalDate.parse(endDate));
    }

    @Test
    public void testRetrieveCurrentDelinquencyTagsReturnsEmptyMapWithoutQueryForNoLoans() {
        Assertions.assertTrue(underTest.retrieveCurrentDelinquencyTags(List.of()).isEmpty());
        verifyNoInteractions(repositoryLoanDelinquencyTagHistory);
    }

    @Test
    public void testRetrieveCurrentDelinquencyTagsMapsCurrentRangePerLoan() {
        DelinquencyRange range1 = mock(DelinquencyRange.class);
        DelinquencyRange range2 = mock(DelinquencyRange.class);
        DelinquencyRangeData rangeData1 = DelinquencyRangeData.instance("D1", 1, 30);
        DelinquencyRangeData rangeData2 = DelinquencyRangeData.instance("D2", 31, 60);
        when(repositoryLoanDelinquencyTagHistory.findCurrentDelinquencyRangesByLoanIds(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(new Object[] { 1L, range1 }, new Object[] { 3L, range2 }));
        when(mapperRange.map(range1)).thenReturn(rangeData1);
        when(mapperRange.map(range2)).thenReturn(rangeData2);

        Map<Long, DelinquencyRangeData> result = underTest.retrieveCurrentDelinquencyTags(List.of(1L, 2L, 3L));

        Assertions.assertEquals(Map.of(1L, rangeData1, 3L, rangeData2), result);
    }

    @Test
    public void testRetrieveCurrentDelinquencyTagsPartitionsLargeIdLists() {
        List<Long> loanIds = LongStream.rangeClosed(1, DatabaseSpecificSQLGenerator.IN_CLAUSE_MAX_PARAMS + 1L).boxed().toList();
        when(repositoryLoanDelinquencyTagHistory.findCurrentDelinquencyRangesByLoanIds(anyList())).thenReturn(List.of());

        underTest.retrieveCurrentDelinquencyTags(loanIds);

        verify(repositoryLoanDelinquencyTagHistory)
                .findCurrentDelinquencyRangesByLoanIds(loanIds.subList(0, DatabaseSpecificSQLGenerator.IN_CLAUSE_MAX_PARAMS));
        verify(repositoryLoanDelinquencyTagHistory)
                .findCurrentDelinquencyRangesByLoanIds(loanIds.subList(DatabaseSpecificSQLGenerator.IN_CLAUSE_MAX_PARAMS, loanIds.size()));
    }
}
