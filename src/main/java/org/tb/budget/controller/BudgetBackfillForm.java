package org.tb.budget.controller;

import lombok.Data;

/** The scope of a backfill run: one customer order, or blank for all of them (#910). */
@Data
public class BudgetBackfillForm {

    private String customerorderSign;

}
