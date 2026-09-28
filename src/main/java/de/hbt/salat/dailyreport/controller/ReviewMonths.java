package de.hbt.salat.dailyreport.controller;

import static de.hbt.salat.common.util.DateUtils.addMonths;
import static de.hbt.salat.common.util.DateUtils.min;

import java.time.LocalDate;
import java.time.YearMonth;
import de.hbt.salat.employee.domain.Employeecontract;

/**
 * The months the pages of release and acceptance propose, and with them the command palette's
 * {@code freigabe} and {@code abnahme} (#1158) — one rule for all three, so the palette never
 * proposes another month than the page it leads to.
 */
final class ReviewMonths {

    private ReviewMonths() {
    }

    /** The month after the last release, or the first month of the contract; never past its end. */
    static YearMonth nextRelease(Employeecontract contract) {
        LocalDate rd = contract.getReportReleaseDate();
        LocalDate defaultDate = rd == null ? contract.getValidFrom() : addMonths(rd, 1);
        return YearMonth.from(min(defaultDate, contract.getValidUntil()));
    }

    /**
     * The month the acceptance proposes (#1122): the one of the release — accepted is what is
     * released. Before anything is released, the month of the last acceptance, or the contract's
     * first; the overview then says why there is nothing to accept.
     */
    static YearMonth nextAcceptance(Employeecontract contract) {
        if (contract.getReportReleaseDate() != null) return YearMonth.from(contract.getReportReleaseDate());
        LocalDate ad = contract.getReportAcceptanceDate();
        LocalDate defaultDate = ad == null ? contract.getValidFrom() : ad;
        return YearMonth.from(min(defaultDate, contract.getValidUntil()));
    }
}
