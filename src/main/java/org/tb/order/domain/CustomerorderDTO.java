package org.tb.order.domain;

import java.time.LocalDate;
import java.util.List;

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
    List<Long> responsibleHbtIds,
    Long respEmpHbtContractId,
    String debithours,
    Byte debithoursunit,
    Boolean hide,
    OrderType orderType
) {}
