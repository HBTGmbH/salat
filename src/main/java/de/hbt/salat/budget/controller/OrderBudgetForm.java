package de.hbt.salat.budget.controller;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import de.hbt.salat.budget.domain.ProgressMode;

@Getter
@Setter
public class OrderBudgetForm {

    private Long id;
    private String name;
    /** The customer order, by id (#1205). */
    private Long customerorderId;
    /** The suborder, by id; {@code null} for a plan on the whole customer order. */
    private Long suborderId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validUntil;

    private Boolean active = Boolean.TRUE;
    private Integer alertThresholdPercent;
    private ProgressMode progressMode;
    /** Whether the plan is a fixed price (#1404). */
    private Boolean fixedPrice = Boolean.FALSE;

    public boolean isNew() {
        return id == null;
    }

}
