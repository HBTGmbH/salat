package org.tb.order.controller;

import java.time.Duration;
import java.time.LocalDate;
import lombok.Data;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;

@Data
public class EmployeeorderForm {

    private Long id;
    private Long employeeContractId;
    private Long orderId;
    private Long suborderId;
    private String validFrom;
    private String validUntil;
    private String debithours;
    private Byte debithoursunit;
    private Boolean showOnlyValid = Boolean.TRUE;

    public LocalDate getValidFromTyped() {
        return DateUtils.parseOrNull(validFrom);
    }

    public LocalDate getValidUntilTyped() {
        if (validUntil == null || validUntil.isBlank()) return null;
        return DateUtils.parseOrNull(validUntil);
    }

    public Duration getDebithoursTyped() {
        if (debithours == null || debithours.isBlank()) return Duration.ZERO;
        return DurationUtils.parseDuration(debithours);
    }

}
