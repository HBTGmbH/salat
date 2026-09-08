package org.tb.budget.controller;

import java.time.LocalDate;
import lombok.Data;
import org.tb.budget.domain.BulkAssignmentData;

/** The selection of a bulk assignment run (#911). */
@Data
public class BulkAssignmentForm {

    private String customerorderSign;

    /** The complete order sign of the suborder, blank for the whole customer order. */
    private String suborderSign;

    private LocalDate from;
    private LocalDate until;
    private Long targetBudgetId;

    /** Off by default — retargeting a booking someone assigned deliberately has to be asked for. */
    private boolean includeAssigned;

    /** Whether the selection is complete enough to be previewed or applied. */
    public boolean isComplete() {
        return customerorderSign != null && !customerorderSign.isBlank()
            && from != null && until != null && !from.isAfter(until)
            && targetBudgetId != null;
    }

    public BulkAssignmentData toData() {
        return new BulkAssignmentData(customerorderSign, suborderSign, from, until,
            targetBudgetId, includeAssigned);
    }

}
