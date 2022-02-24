package org.tb.bdom;

import static org.tb.util.TimeFormatUtils.timeFormatMinutes;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.tb.GlobalConstants;
import org.tb.persistence.TimereportDAO;

@RequiredArgsConstructor
public class InvoiceSuborderActualHoursVisitor implements SuborderVisitor {

    private final TimereportDAO timereportDAO;
    private final LocalDate fromDate;
    private final LocalDate untilDate;
    private final boolean invoicebox;
    private Long durationMinutes;

    public void visitSuborder(Suborder suborder) {
        if (invoicebox && GlobalConstants.SUBORDER_INVOICE_YES == suborder.getInvoice()) {
            durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
        } else if (invoicebox && GlobalConstants.SUBORDER_INVOICE_YES == suborder.getInvoice()) {
            durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
        } else if (!invoicebox && GlobalConstants.SUBORDER_INVOICE_YES == suborder.getInvoice()) {
            durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
        }
    }

    public String getTotalTime() {
        return timeFormatMinutes(durationMinutes);
    }

    public long getTotalMinutes() {
        return durationMinutes;
    }
}
