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
package org.apache.fineract.portfolio.savings.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.portfolio.savings.data.DepositAccountOnHoldTransactionData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
public class DepositAccountOnHoldTransactionReadPlatformServiceImpl implements DepositAccountOnHoldTransactionReadPlatformService {

    private static final String RESOURCE_NAME = "savingsaccount.onholdtransactions";
    // allowed orderBy values mapped to the SQL column used for sorting
    private static final Map<String, String> SUPPORTED_ORDER_BY_VALUES = Map.ofEntries(Map.entry("id", "tr.id"),
            Map.entry("transactionId", "tr.id"), Map.entry("tr.id", "tr.id"), Map.entry("transactionDate", "tr.transaction_date"),
            Map.entry("tr.transaction_date", "tr.transaction_date"), Map.entry("amount", "tr.amount"),
            Map.entry("transactionAmount", "tr.amount"), Map.entry("tr.amount", "tr.amount"),
            Map.entry("transactionType", "tr.transaction_type_enum"), Map.entry("tr.transaction_type_enum", "tr.transaction_type_enum"),
            Map.entry("reversed", "tr.is_reversed"), Map.entry("tr.is_reversed", "tr.is_reversed"));
    private static final Set<String> SUPPORTED_SORT_ORDER_VALUES = Set.of("ASC", "DESC");

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final PaginationHelper paginationHelper;
    private final DepositAccountOnHoldTransactionsMapper mapper = new DepositAccountOnHoldTransactionsMapper();

    @Override
    public Page<DepositAccountOnHoldTransactionData> retriveAll(Long savingsId, Long guarantorFundingId,
            SearchParameters searchParameters) {
        final String orderByColumn = resolveOrderByColumn(searchParameters);
        final String sortOrder = resolveSortOrder(searchParameters);

        final StringBuilder sqlBuilder = new StringBuilder(200);
        List<Long> paramObj = new ArrayList<>(2);
        sqlBuilder.append("select " + sqlGenerator.calcFoundRows() + " ");
        sqlBuilder.append(this.mapper.schema());

        sqlBuilder.append(" where tr.savings_account_id = ? ");
        paramObj.add(savingsId);
        if (guarantorFundingId != null) {
            sqlBuilder.append(" and gt.guarantor_fund_detail_id = ? ");
            paramObj.add(guarantorFundingId);
        }

        if (orderByColumn != null) {
            sqlBuilder.append(" order by ").append(orderByColumn);
            if (sortOrder != null) {
                sqlBuilder.append(' ').append(sortOrder);
            }
        }

        if (searchParameters.hasLimit()) {
            sqlBuilder.append(" ");
            if (searchParameters.hasOffset()) {
                sqlBuilder.append(sqlGenerator.limit(searchParameters.getLimit(), searchParameters.getOffset()));
            } else {
                sqlBuilder.append(sqlGenerator.limit(searchParameters.getLimit()));
            }
        }

        final Object[] finalObjectArray = paramObj.toArray();
        return this.paginationHelper.fetchPage(this.jdbcTemplate, sqlBuilder.toString(), finalObjectArray, this.mapper);

    }

    private static String resolveOrderByColumn(final SearchParameters searchParameters) {
        if (!searchParameters.hasOrderBy()) {
            return null;
        }
        final String orderBy = searchParameters.getOrderBy().trim();
        final String column = SUPPORTED_ORDER_BY_VALUES.get(orderBy);
        if (column == null) {
            throw validationError("orderBy", orderBy, SUPPORTED_ORDER_BY_VALUES.keySet());
        }
        return column;
    }

    private static String resolveSortOrder(final SearchParameters searchParameters) {
        if (!searchParameters.hasSortOrder()) {
            return null;
        }
        final String sortOrder = searchParameters.getSortOrder().trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SORT_ORDER_VALUES.contains(sortOrder)) {
            throw validationError("sortOrder", searchParameters.getSortOrder(), SUPPORTED_SORT_ORDER_VALUES);
        }
        return sortOrder;
    }

    private static PlatformApiDataValidationException validationError(final String parameter, final String value,
            final Set<String> supportedValues) {
        final String supported = new TreeSet<>(supportedValues).toString();
        final String defaultUserMessage = "The " + parameter + " value '" + value + "' is not supported. The supported " + parameter
                + " values are " + supported;
        return new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError(
                "validation.msg." + RESOURCE_NAME + "." + parameter + ".value.is.not.supported", defaultUserMessage, parameter, value,
                supported)));
    }

    private static final class DepositAccountOnHoldTransactionsMapper implements RowMapper<DepositAccountOnHoldTransactionData> {

        private final String schemaSql;

        DepositAccountOnHoldTransactionsMapper() {

            final StringBuilder sqlBuilder = new StringBuilder(400);
            sqlBuilder.append(" tr.id as transactionId, tr.transaction_type_enum as transactionType, ");
            sqlBuilder.append(" tr.transaction_date as transactionDate, tr.amount as transactionAmount,");
            sqlBuilder.append(" tr.is_reversed as reversed, sa.account_no as savingsAccNum, ");
            sqlBuilder.append(" COALESCE(sc.display_name, sg.display_name) as savingsClientName, ml.id as loanid, sa.id as savingid, ");
            sqlBuilder.append(" ml.account_no as loanAccountNum, COALESCE(lc.display_name, lg.display_name) as loanClientName");
            sqlBuilder.append(" from m_savings_account sa  ");
            sqlBuilder.append(" join m_deposit_account_on_hold_transaction tr on sa.id = tr.savings_account_id ");
            sqlBuilder.append(" left join m_client sc on sc.id = sa.client_id");
            sqlBuilder.append(" left join m_group sg on sg.id = sa.group_id");
            sqlBuilder.append(" left join m_guarantor_transaction gt on gt.deposit_on_hold_transaction_id = tr.id ");
            sqlBuilder.append(" left join m_guarantor_funding_details mgfd on mgfd.id=gt.guarantor_fund_detail_id");
            sqlBuilder.append(" left join m_portfolio_account_associations  pa on pa.id=mgfd.account_associations_id");
            sqlBuilder.append(" left join m_loan ml on ml.id = pa.loan_account_id");
            sqlBuilder.append(" left join m_client lc on lc.id = ml.client_id");
            sqlBuilder.append(" left join m_group lg on lg.id = ml.group_id");

            this.schemaSql = sqlBuilder.toString();
        }

        public String schema() {
            return this.schemaSql;
        }

        @Override
        public DepositAccountOnHoldTransactionData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum)
                throws SQLException {
            final Long id = rs.getLong("transactionId");
            final int transactionTypeInt = JdbcSupport.getInteger(rs, "transactionType");
            final EnumOptionData transactionType = SavingsEnumerations.onHoldTransactionType(transactionTypeInt);

            final LocalDate date = JdbcSupport.getLocalDate(rs, "transactionDate");
            final BigDecimal amount = JdbcSupport.getBigDecimalDefaultToZeroIfNull(rs, "transactionAmount");
            final boolean reversed = rs.getBoolean("reversed");
            final String savingsAccountNum = rs.getString("savingsAccNum");
            final Long savingsId = rs.getLong("savingid");
            final String savingsClientName = rs.getString("savingsClientName");
            final String loanAccountNum = rs.getString("loanAccountNum");
            final Long loanId = rs.getLong("loanid");
            final String loanClientName = rs.getString("loanClientName");
            return DepositAccountOnHoldTransactionData.instance(id, amount, transactionType, date, reversed, savingsId, savingsAccountNum,
                    savingsClientName, loanId, loanAccountNum, loanClientName);
        }
    }

}
