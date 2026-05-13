package org.tb.order.domain;

import java.time.Duration;
import java.time.LocalDate;

public record EmployeeorderListItemDTO(
    Long id,
    boolean currentlyValid,
    boolean fitsToSuperiorObjects,
    String customerShortName,
    String employeeName,
    String completeOrderSign,
    String completeOrderDescription,
    LocalDate fromDate,
    LocalDate untilDate,
    Duration debithours,
    Byte debithoursunit,
    Duration duration,
    Duration difference
) {}
