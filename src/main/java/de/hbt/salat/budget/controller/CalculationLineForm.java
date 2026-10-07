package de.hbt.salat.budget.controller;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** A line of the calculation of a fixed-price plan, as the detail page edits it (#1404). */
@Getter
@Setter
public class CalculationLineForm {

    /** The line being changed; {@code null} adds a new one. */
    private Long id;
    private Long suborderId;
    private Long categoryId;
    /** Decimal hours, e.g. 7.5. */
    private BigDecimal hours;

}
