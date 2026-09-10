package org.tb.budget.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.format.annotation.NumberFormat.Style;
import org.tb.budget.domain.FlatRateRhythm;

@Getter
@Setter
public class OrderFlatRateForm {

    private Long id;
    private String customerorderSign;
    private String suborderSign;
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
