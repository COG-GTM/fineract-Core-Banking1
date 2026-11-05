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
package org.apache.fineract.infrastructure.reportmailingjob.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.reportmailingjob.ReportMailingJobConstants;
import org.apache.fineract.infrastructure.reportmailingjob.data.ReportMailingJobRunHistoryData;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class ReportMailingJobRunHistoryReadPlatformServiceImpl implements ReportMailingJobRunHistoryReadPlatformService {

    private static final Map<String, String> SORTABLE_COLUMNS = Map.ofEntries(Map.entry("id", "rmjrh.id"),
            Map.entry("job_id", "rmjrh.job_id"), Map.entry("reportmailingjobid", "rmjrh.job_id"),
            Map.entry("start_datetime", "rmjrh.start_datetime"), Map.entry("startdatetime", "rmjrh.start_datetime"),
            Map.entry("end_datetime", "rmjrh.end_datetime"), Map.entry("enddatetime", "rmjrh.end_datetime"),
            Map.entry("status", "rmjrh.status"));
    private static final Set<String> SORT_ORDERS = Set.of("ASC", "DESC");

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final ReportMailingJobRunHistoryMapper reportMailingJobRunHistoryMapper;
    private final ColumnValidator columnValidator;
    private final PaginationHelper paginationHelper;

    @Autowired
    public ReportMailingJobRunHistoryReadPlatformServiceImpl(final JdbcTemplate jdbcTemplate, final ColumnValidator columnValidator,
            DatabaseSpecificSQLGenerator sqlGenerator, PaginationHelper paginationHelper) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlGenerator = sqlGenerator;
        this.reportMailingJobRunHistoryMapper = new ReportMailingJobRunHistoryMapper();
        this.columnValidator = columnValidator;
        this.paginationHelper = paginationHelper;
    }

    @Override
    public Page<ReportMailingJobRunHistoryData> retrieveRunHistoryByJobId(final Long reportMailingJobId,
            final SearchParameters searchParameters) {
        final StringBuilder sqlStringBuilder = new StringBuilder(200);
        final List<Object> queryParameters = new ArrayList<>();

        sqlStringBuilder.append("select " + sqlGenerator.calcFoundRows() + " ");
        sqlStringBuilder.append(this.reportMailingJobRunHistoryMapper.reportMailingJobRunHistorySchema());

        if (reportMailingJobId != null) {
            sqlStringBuilder.append(" where rmjrh.job_id = ? ");
            queryParameters.add(reportMailingJobId);
        }

        if (searchParameters.hasOrderBy()) {
            final String orderByColumn = resolveOrderByColumn(searchParameters.getOrderBy());
            sqlStringBuilder.append(" order by ").append(orderByColumn);
            this.columnValidator.validateSqlInjection(sqlStringBuilder.toString(), orderByColumn);
            if (searchParameters.hasSortOrder()) {
                final String sortOrder = resolveSortOrder(searchParameters.getSortOrder());
                sqlStringBuilder.append(" ").append(sortOrder);
                this.columnValidator.validateSqlInjection(sqlStringBuilder.toString(), sortOrder);
            }
        }

        if (searchParameters.hasLimit()) {
            sqlStringBuilder.append(" ");
            if (searchParameters.hasOffset()) {
                sqlStringBuilder.append(sqlGenerator.limit(searchParameters.getLimit(), searchParameters.getOffset()));
            } else {
                sqlStringBuilder.append(sqlGenerator.limit(searchParameters.getLimit()));
            }
        }

        return this.paginationHelper.fetchPage(this.jdbcTemplate, sqlStringBuilder.toString(), queryParameters.toArray(),
                this.reportMailingJobRunHistoryMapper);
    }

    private static String resolveOrderByColumn(final String orderBy) {
        final String column = SORTABLE_COLUMNS.get(orderBy.trim().toLowerCase(Locale.ROOT));
        if (column == null) {
            throw unsupportedValue("orderBy", orderBy, new TreeSet<>(SORTABLE_COLUMNS.keySet()).toString());
        }
        return column;
    }

    private static String resolveSortOrder(final String sortOrder) {
        final String normalized = sortOrder.trim().toUpperCase(Locale.ROOT);
        if (!SORT_ORDERS.contains(normalized)) {
            throw unsupportedValue("sortOrder", sortOrder, "[ASC, DESC]");
        }
        return normalized;
    }

    private static PlatformApiDataValidationException unsupportedValue(final String parameterName, final String value,
            final String supportedValues) {
        final String defaultUserMessage = "The " + parameterName + " value '" + value + "' is not supported. The supported " + parameterName
                + " values are " + supportedValues;
        final ApiParameterError error = ApiParameterError.parameterErrorWithValue(
                "validation.msg." + ReportMailingJobConstants.REPORT_MAILING_JOB_RUN_HISTORY_RESOURCE_NAME + "." + parameterName
                        + ".value.is.not.supported",
                defaultUserMessage, parameterName, value, supportedValues);
        return new PlatformApiDataValidationException(List.of(error));
    }

    private static final class ReportMailingJobRunHistoryMapper implements RowMapper<ReportMailingJobRunHistoryData> {

        public String reportMailingJobRunHistorySchema() {
            return "rmjrh.id, rmjrh.job_id as reportMailingJobId, rmjrh.start_datetime as startDateTime, "
                    + "rmjrh.end_datetime as endDateTime, rmjrh.status, rmjrh.error_message as errorMessage, "
                    + "rmjrh.error_log as errorLog " + "from m_report_mailing_job_run_history rmjrh";
        }

        @Override
        public ReportMailingJobRunHistoryData mapRow(ResultSet rs, int rowNum) throws SQLException {
            final Long id = JdbcSupport.getLong(rs, "id");
            final Long reportMailingJobId = JdbcSupport.getLong(rs, "reportMailingJobId");
            final ZonedDateTime startDateTime = JdbcSupport.getDateTime(rs, "startDateTime");
            final ZonedDateTime endDateTime = JdbcSupport.getDateTime(rs, "endDateTime");
            final String status = rs.getString("status");
            final String errorMessage = rs.getString("errorMessage");
            final String errorLog = rs.getString("errorLog");

            return ReportMailingJobRunHistoryData.newInstance(id, reportMailingJobId, startDateTime, endDateTime, status, errorMessage,
                    errorLog);
        }
    }
}
