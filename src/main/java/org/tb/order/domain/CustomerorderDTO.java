package org.tb.order.domain;

import java.time.LocalDate;

/**
 * Data transfer object carrying customer order field values for create/update operations.
 */
public record CustomerorderDTO(
    long customerId,
    LocalDate fromDate,
    LocalDate untilDate,
    String sign,
    String description,
    String shortdescription,
    String orderCustomer,
    String responsibleCustomerContractually,
    String responsibleCustomerTechnical,
    long responsibleHbtId,
    long respEmpHbtContractId,
    String debithours,
    Byte debithoursunit,
    Boolean hide,
    OrderType orderType
) {}
