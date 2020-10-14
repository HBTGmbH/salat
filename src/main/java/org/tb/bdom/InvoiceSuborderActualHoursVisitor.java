package org.tb.bdom;

import lombok.RequiredArgsConstructor;
import org.tb.GlobalConstants;
import org.tb.persistence.TimereportDAO;

import java.sql.Date;
import java.text.DecimalFormat;

@RequiredArgsConstructor
public class InvoiceSuborderActualHoursVisitor implements SuborderVisitor {

    private final TimereportDAO timereportDAO;
    private final Date fromDate;
    private final Date untilDate;
    private final boolean invoicebox;
    private Long durationMinutes;

    public void visitSuborder(Suborder suborder) {
        if (invoicebox && GlobalConstants.INVOICE_NO.equals(suborder.getInvoice())) {
            durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
        } else if (invoicebox && GlobalConstants.INVOICE_YES.equals(suborder.getInvoice())) {
            durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
        } else if (!invoicebox && GlobalConstants.INVOICE_YES.equals(suborder.getInvoice())) {
            durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
        }
    }

    public String getTotalTime() {
        DecimalFormat decimalFormat = new DecimalFormat("00");
        return decimalFormat.format(durationMinutes / 60) + ":" + decimalFormat.format(durationMinutes % 60);
    }

    public long getTotalMinutes() {
        return durationMinutes;
    }
}
