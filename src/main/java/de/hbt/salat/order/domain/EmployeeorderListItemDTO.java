package de.hbt.salat.order.domain;

import java.time.Duration;
import java.time.LocalDate;

public record EmployeeorderListItemDTO(
    Long id,
    boolean currentlyValid,
    boolean fitsToSuperiorObjects,
    boolean hidden,
    String customerShortName,
    String customerName,
    /** the short name of the order's intermediary (#1536), {@code null} for an order placed directly */
    String intermediaryShortName,
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
