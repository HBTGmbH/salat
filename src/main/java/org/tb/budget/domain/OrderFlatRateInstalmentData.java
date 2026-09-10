package org.tb.budget.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderFlatRateInstalmentData(
    BigDecimal amount,
    LocalDate due,
    String comment
) {}
