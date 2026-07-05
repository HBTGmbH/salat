package org.tb.budget.domain;

import java.time.LocalDate;

public record OrderPricingData(
    String customerorderSign,
    String suborderSign,
    String employeeSign,
    String description,
    Integer priceCentsPerHour,
    LocalDate validFrom,
    LocalDate validUntil
) {}
