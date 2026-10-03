package de.hbt.salat.budget.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.format.annotation.NumberFormat.Style;
import de.hbt.salat.budget.domain.FlatRateRhythm;

@Getter
@Setter
public class OrderFlatRateForm {

    private Long id;
    /** The customer order, by id (#1205). */
    private Long customerorderId;
    /** The suborder, by id; {@code null} for the whole customer order. */
    private Long suborderId;
    /** The stored suborder sign of a flat rate the migration could not resolve — a marker only. */
    private String unresolvedSuborderSign;

    /** Optional (#1065): without it the plan is derived, as it was before. */
    private Long orderBudgetId;

    private String description;

    /** Preselected, because it is the simplest case and the one that needs the fewest fields. */
    private FlatRateRhythm rhythm = FlatRateRhythm.ONCE;

    @NumberFormat(style = Style.NUMBER)
    private BigDecimal amountEuro;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validUntil;

    public boolean isNew() {
        return id == null;
    }

    /** A single amount is due on one day, so the form does not ask for an end. */
    public boolean needsValidUntil() {
        return rhythm != FlatRateRhythm.ONCE;
    }

    public boolean needsAmount() {
        return rhythm != null && rhythm.hasOwnAmount();
    }

}
