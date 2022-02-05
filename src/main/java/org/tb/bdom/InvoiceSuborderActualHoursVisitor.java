package org.tb.bdom;

import static org.tb.util.TimeFormatUtils.timeFormatMinutes;

import lombok.RequiredArgsConstructor;
import org.tb.GlobalConstants;
import org.tb.persistence.TimereportDAO;

import java.sql.Date;
import java.text.DecimalFormat;
import org.tb.util.TimeFormatUtils;

@RequiredArgsConstructor
public class InvoiceSuborderActualHoursVisitor implements SuborderVisitor {

    private final TimereportDAO timereportDAO;
    private final Date fromDate;
    private final Date untilDate;
    private final boolean invoicebox;
    private Long durationMinutes;

    public void visitSuborder(Suborder suborder) {
        if (invoicebox && GlobalConstants.INVOICE_NO == suborder.getInvoice()) {
            durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
        } else if (invoicebox && GlobalConstants.INVOICE_YES == suborder.getInvoice()) {
            durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
        } else if (!invoicebox && GlobalConstants.INVOICE_YES == suborder.getInvoice()) {
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
