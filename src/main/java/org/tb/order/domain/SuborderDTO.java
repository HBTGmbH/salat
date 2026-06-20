package org.tb.order.domain;

/**
 * Data transfer object carrying suborder field values for create/update operations.
 */
public record SuborderDTO(
    Long customerorderId,
    String sign,
    String description,
    String shortdescription,
    String suborder_customer,
    char invoice,
    Boolean standard,
    Boolean commentnecessary,
    Boolean fixedPrice,
    Boolean trainingFlag,
    OrderType orderType,
    String validFrom,
    String validUntil,
    String debithours,
    Byte debithoursunit,
    Boolean hide,
    Long parentId
) {}
