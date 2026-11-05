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
package org.apache.fineract.portfolio.account.jobs.executestandinginstructions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.account.data.AccountTransferDTO;
import org.apache.fineract.portfolio.account.data.PortfolioAccountData;
import org.apache.fineract.portfolio.account.data.StandingInstructionData;
import org.apache.fineract.portfolio.account.data.StandingInstructionDuesData;
import org.apache.fineract.portfolio.account.domain.AccountTransferRecurrenceType;
import org.apache.fineract.portfolio.account.domain.AccountTransferType;
import org.apache.fineract.portfolio.account.domain.StandingInstructionStatus;
import org.apache.fineract.portfolio.account.domain.StandingInstructionType;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.account.service.StandingInstructionReadPlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

public class ExecuteStandingInstructionsTaskletTest {

    private static final Long INSTRUCTION_ID = 7L;
    private static final String EXPECTED_HISTORY_INSERT = "INSERT INTO m_account_transfer_standing_instructions_history "
            + "(standing_instruction_id, \"status\", amount, execution_time, error_log) VALUES (?, ?, ?, now(), ?)";

    private final StandingInstructionReadPlatformService readPlatformService = mock(StandingInstructionReadPlatformService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DatabaseSpecificSQLGenerator sqlGenerator = mock(DatabaseSpecificSQLGenerator.class);
    private final AccountTransfersWritePlatformService transfersWritePlatformService = mock(AccountTransfersWritePlatformService.class);
    private final ExecuteStandingInstructionsTasklet tasklet = new ExecuteStandingInstructionsTasklet(readPlatformService, jdbcTemplate,
            sqlGenerator, transfersWritePlatformService);

    @BeforeEach
    public void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.now(ZoneId.systemDefault()))));
        when(sqlGenerator.escape("status")).thenReturn("\"status\"");
        StandingInstructionData instruction = dueLoanInstruction();
        when(readPlatformService.retrieveAll(StandingInstructionStatus.ACTIVE.getValue())).thenReturn(List.of(instruction));
        when(readPlatformService.retriveLoanDuesData(2L))
                .thenReturn(new StandingInstructionDuesData(LocalDate.now(ZoneId.systemDefault()).minusDays(2), BigDecimal.TEN));
    }

    @AfterEach
    public void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    public void successfulTransferIsRecordedWithBindParameters() throws Exception {
        RepeatStatus status = tasklet.execute(null, null);

        assertEquals(RepeatStatus.FINISHED, status);
        verify(jdbcTemplate).update(eq(EXPECTED_HISTORY_INSERT), eq(INSTRUCTION_ID), eq("success"), eq(100.0d), eq(""));
        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    public void exceptionMessageWithQuotesIsBoundNotConcatenated() {
        String message = "account O'Brien'); DELETE FROM m_savings_account; --";
        doThrow(new IllegalStateException(message)).when(transfersWritePlatformService).transferFunds(any(AccountTransferDTO.class));

        assertThrows(JobExecutionException.class, () -> tasklet.execute(null, null));

        verify(jdbcTemplate).update(eq(EXPECTED_HISTORY_INSERT), eq(INSTRUCTION_ID), eq("failed"), eq(100.0d),
                eq("Exception while trasfering funds " + message));
        verify(jdbcTemplate, never()).update(anyString());
    }

    @Test
    public void validationMessageWithQuotesIsBoundNotConcatenated() {
        String message = "Instruction 'Rent' is invalid";
        doThrow(new PlatformApiDataValidationException("error.msg.invalid", message, List.of())).when(transfersWritePlatformService)
                .transferFunds(any(AccountTransferDTO.class));

        assertThrows(JobExecutionException.class, () -> tasklet.execute(null, null));

        verify(jdbcTemplate).update(eq(EXPECTED_HISTORY_INSERT), eq(INSTRUCTION_ID), eq("failed"), eq(100.0d),
                eq("Validation exception while trasfering funds " + message));
        verify(jdbcTemplate, never()).update(anyString());
    }

    private static StandingInstructionData dueLoanInstruction() {
        PortfolioAccountData fromAccount = mock(PortfolioAccountData.class);
        when(fromAccount.getId()).thenReturn(1L);
        PortfolioAccountData toAccount = mock(PortfolioAccountData.class);
        when(toAccount.getId()).thenReturn(2L);

        StandingInstructionData instruction = mock(StandingInstructionData.class);
        when(instruction.getId()).thenReturn(INSTRUCTION_ID);
        when(instruction.getName()).thenReturn("Rent");
        when(instruction.getRecurrenceType()).thenReturn(AccountTransferRecurrenceType.AS_PER_DUES);
        when(instruction.getInstructionType()).thenReturn(StandingInstructionType.FIXED);
        when(instruction.getAmount()).thenReturn(new BigDecimal("100"));
        when(instruction.getFromAccountType()).thenReturn(PortfolioAccountType.SAVINGS);
        when(instruction.getToAccountType()).thenReturn(PortfolioAccountType.LOAN);
        when(instruction.getFromAccount()).thenReturn(fromAccount);
        when(instruction.getToAccount()).thenReturn(toAccount);
        when(instruction.getTransferType()).thenReturn(AccountTransferType.LOAN_REPAYMENT);
        return instruction;
    }
}
