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
package org.apache.fineract.infrastructure.bulkimport.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.fineract.accounting.glaccount.service.GLAccountReadPlatformService;
import org.apache.fineract.infrastructure.bulkimport.constants.TemplatePopulateImportConstants;
import org.apache.fineract.infrastructure.bulkimport.data.GlobalEntityType;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.service.CurrencyReadPlatformService;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.organisation.staff.service.StaffReadPlatformService;
import org.apache.fineract.portfolio.charge.service.ChargeReadPlatformService;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.apache.fineract.portfolio.fund.service.FundReadPlatformService;
import org.apache.fineract.portfolio.group.service.CenterReadPlatformService;
import org.apache.fineract.portfolio.group.service.GroupReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.portfolio.loanproduct.service.LoanProductReadPlatformService;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadService;
import org.apache.fineract.portfolio.products.service.ShareProductReadPlatformService;
import org.apache.fineract.portfolio.savings.service.DepositProductReadPlatformService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountReadPlatformService;
import org.apache.fineract.portfolio.savings.service.SavingsProductReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.service.RoleReadPlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkImportWorkbookPopulatorServiceImplTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private OfficeReadPlatformService officeReadPlatformService;
    @Mock
    private StaffReadPlatformService staffReadPlatformService;
    @Mock
    private ClientReadPlatformService clientReadPlatformService;
    @Mock
    private CenterReadPlatformService centerReadPlatformService;
    @Mock
    private GroupReadPlatformService groupReadPlatformService;
    @Mock
    private FundReadPlatformService fundReadPlatformService;
    @Mock
    private PaymentTypeReadService paymentTypeReadPlatformService;
    @Mock
    private LoanProductReadPlatformService loanProductReadPlatformService;
    @Mock
    private CurrencyReadPlatformService currencyReadPlatformService;
    @Mock
    private LoanReadPlatformService loanReadPlatformService;
    @Mock
    private GLAccountReadPlatformService glAccountReadPlatformService;
    @Mock
    private SavingsAccountReadPlatformService savingsAccountReadPlatformService;
    @Mock
    private CodeValueReadPlatformService codeValueReadPlatformService;
    @Mock
    private SavingsProductReadPlatformService savingsProductReadPlatformService;
    @Mock
    private ShareProductReadPlatformService shareProductReadPlatformService;
    @Mock
    private ChargeReadPlatformService chargeReadPlatformService;
    @Mock
    private DepositProductReadPlatformService depositProductReadPlatformService;
    @Mock
    private RoleReadPlatformService roleReadPlatformService;

    @InjectMocks
    private BulkImportWorkbookPopulatorServiceImpl service;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.now(ZoneId.systemDefault()))));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    private void denyRead(String resourceType) {
        doThrow(new NoAuthorizationException("User has no authority to READ " + resourceType.toLowerCase(Locale.ROOT) + "s")).when(user)
                .validateHasReadPermission(resourceType);
    }

    private void assertNoAccountDataRead() {
        verifyNoInteractions(officeReadPlatformService, staffReadPlatformService, clientReadPlatformService, loanReadPlatformService,
                savingsAccountReadPlatformService, roleReadPlatformService);
    }

    @ParameterizedTest
    @ValueSource(strings = { TemplatePopulateImportConstants.CLIENT_ENTITY_TYPE, TemplatePopulateImportConstants.LOAN_ENTITY_TYPE,
            TemplatePopulateImportConstants.SAVINGS_ACCOUNT_ENTITY_TYPE })
    void guarantorTemplateRequiresClientLoanAndSavingsReadPermission(String missingPermission) {
        denyRead(missingPermission);

        assertThrows(NoAuthorizationException.class,
                () -> service.getTemplate(GlobalEntityType.GUARANTORS.toString(), 1L, null, "dd MMMM yyyy"));

        assertNoAccountDataRead();
    }

    @ParameterizedTest
    @ValueSource(strings = { TemplatePopulateImportConstants.OFFICE_ENTITY_TYPE,
            TemplatePopulateImportConstants.SAVINGS_ACCOUNT_ENTITY_TYPE })
    void shareAccountTemplateRequiresOfficeAndSavingsReadPermission(String missingPermission) {
        denyRead(missingPermission);

        assertThrows(NoAuthorizationException.class,
                () -> service.getTemplate(GlobalEntityType.SHARE_ACCOUNTS.toString(), 1L, null, "dd MMMM yyyy"));

        assertNoAccountDataRead();
        verifyNoInteractions(shareProductReadPlatformService, chargeReadPlatformService);
    }

    @ParameterizedTest
    @ValueSource(strings = { TemplatePopulateImportConstants.OFFICE_ENTITY_TYPE, TemplatePopulateImportConstants.STAFF_ENTITY_TYPE })
    void userTemplateRequiresOfficeAndStaffReadPermission(String missingPermission) {
        denyRead(missingPermission);

        assertThrows(NoAuthorizationException.class,
                () -> service.getTemplate(GlobalEntityType.USERS.toString(), 1L, null, "dd MMMM yyyy"));

        assertNoAccountDataRead();
    }

    @Test
    void guarantorTemplateIsReturnedWhenUserHasAllRequiredReadPermissions() {
        when(officeReadPlatformService.retrieveAllOffices(anyBoolean(), any())).thenReturn(new ArrayList<>());
        when(clientReadPlatformService.retrieveAll(any())).thenReturn(new Page<>(new ArrayList<>(), 0));
        when(loanReadPlatformService.retrieveAll(any())).thenReturn(new Page<>(new ArrayList<>(), 0));
        when(savingsAccountReadPlatformService.retrieveAll(any())).thenReturn(new Page<>(new ArrayList<>(), 0));
        when(codeValueReadPlatformService.retrieveCodeValuesByCode("GuarantorRelationship")).thenReturn(new ArrayList<>());

        Response response = service.getTemplate(GlobalEntityType.GUARANTORS.toString(), null, null, "dd MMMM yyyy");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        for (String permission : List.of(TemplatePopulateImportConstants.OFFICE_ENTITY_TYPE,
                TemplatePopulateImportConstants.STAFF_ENTITY_TYPE, TemplatePopulateImportConstants.CLIENT_ENTITY_TYPE,
                TemplatePopulateImportConstants.LOAN_ENTITY_TYPE, TemplatePopulateImportConstants.SAVINGS_ACCOUNT_ENTITY_TYPE)) {
            verify(user).validateHasReadPermission(permission);
        }
    }
}
