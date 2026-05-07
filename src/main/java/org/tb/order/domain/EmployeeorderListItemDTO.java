package org.tb.order.domain;

import java.time.Duration;
import java.time.LocalDate;

public record EmployeeorderListItemDTO(
    Long id,
    boolean currentlyValid,
    boolean fitsToSuperiorObjects,
    String employeeName,
    String customerOrderSign,
    String customerOrderDescription,
    String completeOrderSign,
    String subOrderDescription,
    LocalDate fromDate,
    LocalDate untilDate,
    Duration debithours,
    Byte debithoursunit,
    Duration duration,
    Duration difference
) {}
