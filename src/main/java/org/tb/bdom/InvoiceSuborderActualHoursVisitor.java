package org.tb.bdom;

import org.tb.GlobalConstants;
import org.tb.persistence.TimereportDAO;

import java.sql.Date;
import java.text.DecimalFormat;

public class InvoiceSuborderActualHoursVisitor implements SuborderVisitor {

    private TimereportDAO timereportDAO;
    private Long durationMinutes;
    private Date fromDate;
    private Date untilDate;
    private boolean invoicebox;

    public InvoiceSuborderActualHoursVisitor(TimereportDAO timereportDAO, Date fromDate, Date untilDate, boolean invoicebox) {
        this.durationMinutes = 0l;
        this.timereportDAO = timereportDAO;
        this.fromDate = fromDate;
        this.untilDate = untilDate;
        this.invoicebox = invoicebox;
    }

    public void visitSuborder(Suborder suborder) {
        if (invoicebox && GlobalConstants.INVOICE_NO.equals(suborder.getInvoice())) {
            durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
        } else if (invoicebox && GlobalConstants.INVOICE_YES.equals(suborder.getInvoice())) {
            durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
//		} else if (!invoicebox && GlobalConstants.INVOICE_NO.equals(suborder.getInvoice())) {
//			// do nothing
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
