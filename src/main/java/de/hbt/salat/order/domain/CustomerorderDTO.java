package de.hbt.salat.order.domain;

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
    OrderType orderType,
    /** never {@code null} for an order (#1326) */
    TicketReferenceMode ticketReferenceMode,
    /** the bound for {@link TicketReferenceMode#LIMITED}, ignored otherwise */
    Integer ticketReferenceLimit,
    /** the time surcharge rates (#1469); never {@code null}, {@link SurchargeInput#NONE} for none */
    SurchargeInput surchargeRates,
    /**
     * the names of the tags (#847), new ones created on saving; {@code null} leaves the tags as they are — what
     * rebuilds the record from the stored entity (a changed validity) does not touch them
     */
    List<String> tagNames
) {}
