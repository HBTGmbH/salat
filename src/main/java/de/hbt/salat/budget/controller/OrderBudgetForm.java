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
    /**
     * The stored suborder sign of a plan whose suborder the migration could not resolve — shown as a
     * marker so that the person editing sees what was meant; read-only.
     */
    private String unresolvedSuborderSign;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validUntil;

    private Boolean active = Boolean.TRUE;
    private Integer alertThresholdPercent;
    private ProgressMode progressMode;

    public boolean isNew() {
        return id == null;
    }

}
