package org.tb.budget.controller;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.tb.budget.domain.ProgressMode;

@Getter
@Setter
public class OrderBudgetForm {

    private Long id;
    private String name;
    private String customerorderSign;
    private String suborderSign;

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
