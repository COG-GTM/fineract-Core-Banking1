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
package org.apache.fineract.portfolio.client.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.portfolio.client.data.ClientChargeDataValidator;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientChargeRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientTransactionRepository;
import org.apache.fineract.portfolio.client.exception.ClientChargeNotFoundException;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientChargeWritePlatformServiceImplTest {

    private static final Long IN_SCOPE_CLIENT_ID = 77L;
    private static final Long OTHER_CLIENTS_CHARGE_ID = 4521L;

    @Mock
    private ClientRepositoryWrapper clientRepository;
    @Mock
    private ClientChargeDataValidator clientChargeDataValidator;
    @Mock
    private ClientChargeRepositoryWrapper clientChargeRepository;
    @Mock
    private ClientTransactionRepository clientTransactionRepository;
    @Mock
    private PaymentDetailWritePlatformService paymentDetailWritePlatformService;
    @Mock
    private JournalEntryWritePlatformService journalEntryWritePlatformService;
    @Mock
    private Client client;
    @Mock
    private JsonCommand command;

    @InjectMocks
    private ClientChargeWritePlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        when(client.getId()).thenReturn(IN_SCOPE_CLIENT_ID);
        when(clientRepository.getActiveClientInUserScope(IN_SCOPE_CLIENT_ID)).thenReturn(client);
        when(clientChargeRepository.findOneWithNotFoundDetection(OTHER_CLIENTS_CHARGE_ID, IN_SCOPE_CLIENT_ID))
                .thenThrow(new ClientChargeNotFoundException(OTHER_CLIENTS_CHARGE_ID, IN_SCOPE_CLIENT_ID));
    }

    @Test
    void waiveChargeRejectsChargeOfAnotherClient() {
        assertThrows(ClientChargeNotFoundException.class, () -> service.waiveCharge(IN_SCOPE_CLIENT_ID, OTHER_CLIENTS_CHARGE_ID));

        verifyNoInteractions(clientTransactionRepository);
    }

    @Test
    void payChargeRejectsChargeOfAnotherClient() {
        when(command.json()).thenReturn("{}");

        assertThrows(ClientChargeNotFoundException.class,
                () -> service.payCharge(IN_SCOPE_CLIENT_ID, OTHER_CLIENTS_CHARGE_ID, command));

        verifyNoInteractions(clientTransactionRepository, paymentDetailWritePlatformService, journalEntryWritePlatformService);
    }

    @Test
    void deleteChargeRejectsChargeOfAnotherClient() {
        assertThrows(ClientChargeNotFoundException.class, () -> service.deleteCharge(IN_SCOPE_CLIENT_ID, OTHER_CLIENTS_CHARGE_ID));

        verify(clientChargeRepository, never()).delete(any());
    }
}
