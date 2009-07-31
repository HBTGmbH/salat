package org.tb.bdom;

import java.sql.Date;
import java.text.DecimalFormat;

import org.tb.GlobalConstants;
import org.tb.persistence.TimereportDAO;

public class InvoiceSuborderActualHoursVisitor implements SuborderVisitor {

	private TimereportDAO timereportDAO;
	private Long durationHours;
	private Long durationMinutes;
	private Date fromDate;
	private Date untilDate;
	private boolean invoicebox;
	
	public InvoiceSuborderActualHoursVisitor(TimereportDAO timereportDAO, Date fromDate, Date untilDate, boolean invoicebox) {
		this.durationHours = 0l;
		this.durationMinutes = 0l;
		this.timereportDAO = timereportDAO;
		this.fromDate = fromDate;
		this.untilDate = untilDate;
		this.invoicebox = invoicebox;
	}

	public void visitSuborder(Suborder suborder) {
		if (invoicebox && GlobalConstants.INVOICE_NO.equals(suborder.getInvoice())) {
			durationHours += timereportDAO.getTotalDurationHoursForSuborder(suborder.getId(), fromDate, untilDate);
			durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);			
		} else if (invoicebox && GlobalConstants.INVOICE_YES.equals(suborder.getInvoice())) {
			durationHours += timereportDAO.getTotalDurationHoursForSuborder(suborder.getId(), fromDate, untilDate);
			durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
//		} else if (!invoicebox && GlobalConstants.INVOICE_NO.equals(suborder.getInvoice())) {
//			// do nothing
		} else if (!invoicebox && GlobalConstants.INVOICE_YES.equals(suborder.getInvoice())) {
			durationHours += timereportDAO.getTotalDurationHoursForSuborder(suborder.getId(), fromDate, untilDate);
			durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
		}
	}

	public String getTotalTime() {
		DecimalFormat decimalFormat = new DecimalFormat("00");
		return decimalFormat.format(durationHours + durationMinutes / 60) + ":" + decimalFormat.format(durationMinutes % 60);
	}	

	public long getTotalMinutes() {
		return durationHours * 60 + durationMinutes;
	}
}
