package org.tb.budget.controller;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class OrderBudgetScopeEntryForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate refdate;

    private Integer percent;
    private String comment;

}
