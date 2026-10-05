package de.hbt.salat.budget.controller;

import lombok.Data;

/** The scope of a backfill run: one customer order, or blank for all of them (#910). */
@Data
public class BudgetBackfillForm {

    /** By id, not by sign (#1339): a sign can be renamed between choosing and running. */
    private Long customerorderId;

}
