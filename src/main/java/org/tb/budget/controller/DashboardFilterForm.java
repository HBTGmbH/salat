package org.tb.budget.controller;

import lombok.Getter;
import lombok.Setter;

/**
 * Dashboard filters. Both fields are {@code null} when the corresponding select is left on
 * "all" — the form always submits the parameter, so an empty value clears the remembered choice
 * instead of silently keeping it (#923).
 */
@Getter
@Setter
public class DashboardFilterForm {

    private Long budgetSegmentId;

    private Long budgetResponsibleId;

}
