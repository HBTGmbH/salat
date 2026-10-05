package de.hbt.salat.budget.controller;

import static java.util.stream.Collectors.toList;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import de.hbt.salat.budget.domain.BulkAssignmentData;
import de.hbt.salat.budget.domain.BulkAssignmentEmployee;

/** The selection of a bulk assignment run (#911). */
@Data
public class BulkAssignmentForm {

    /** By id, not by sign (#1339): a sign can be renamed between preview and run. */
    private Long customerorderId;

    /** The suborder, {@code null} for the whole customer order. */
    private Long suborderId;

    /**
     * ISO both ways. Without the annotation Spring prints a {@code LocalDate} in the short German
     * form, and {@code th:field} on an {@code <input type="date">} then emits a value the browser
     * discards — so a prefilled or re-rendered period would silently come up empty (#913).
     */
    @DateTimeFormat(iso = ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = ISO.DATE)
    private LocalDate until;

    private Long targetBudgetId;

    /**
     * The people the selection is narrowed to (#953), empty for all of them. Optional, so it has no
     * say in {@link #isComplete()}.
     */
    private List<Long> employeeIds = new ArrayList<>();

    /** Off by default — retargeting a booking someone assigned deliberately has to be asked for. */
    private boolean includeAssigned;

    /** Whether the selection is complete enough to be previewed or applied. */
    public boolean isComplete() {
        return customerorderId != null
            && from != null && until != null && !from.isAfter(until)
            && targetBudgetId != null;
    }

    public BulkAssignmentData toData() {
        return new BulkAssignmentData(customerorderId, suborderId, from, until,
            targetBudgetId, employeeIds == null ? List.of() : employeeIds, includeAssigned);
    }

    /**
     * Drops everyone who no longer has bookings in the changed selection (#953) — a leftover choice
     * would silently narrow the run to a person the option list does not even show any more.
     */
    public void retainEmployees(List<BulkAssignmentEmployee> selectable) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return;
        }
        var available = selectable.stream().map(BulkAssignmentEmployee::id).toList();
        employeeIds = employeeIds.stream().filter(available::contains).collect(toList());
    }

}
