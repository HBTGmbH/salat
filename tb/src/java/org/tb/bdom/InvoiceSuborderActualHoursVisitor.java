package org.tb.bdom;

import org.tb.persistence.TimereportDAO;

public class InvoiceSuborderActualHoursVisitor implements SuborderVisitor {

	private TimereportDAO timereportDAO;
	private Long durationHours;
	private Long durationMinutes;
	private java.sql.Date fromDate;
	private java.sql.Date untilDate;
	private boolean invoicebox;
	
	public InvoiceSuborderActualHoursVisitor(TimereportDAO timereportDAO, java.sql.Date fromDate, java.sql.Date untilDate, boolean invoicebox) {
		this.durationHours = 0l;
		this.durationMinutes = 0l;
		this.timereportDAO = timereportDAO;
		this.fromDate = fromDate;
		this.untilDate = untilDate;
		this.invoicebox = invoicebox;
	}
	

	public void visitSuborder(Suborder suborder) {
		if(invoicebox && suborder.getInvoice() == 'N'){
			durationHours += timereportDAO.getTotalDurationHoursForSuborder(suborder.getId(), fromDate, untilDate);
			durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);			
		}else if (invoicebox && suborder.getInvoice() == 'Y') {
			durationHours += timereportDAO.getTotalDurationHoursForSuborder(suborder.getId(), fromDate, untilDate);
			durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
		}else if (!invoicebox && suborder.getInvoice() == 'N') {
			
		}else if (!invoicebox && suborder.getInvoice() == 'Y') {
			durationHours += timereportDAO.getTotalDurationHoursForSuborder(suborder.getId(), fromDate, untilDate);
			durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
		}
		
			
	}

	

	public String getTotalTime() {
//		double totalTime = durationHours.doubleValue() + (durationMinutes.doubleValue() / GlobalConstants.MINUTES_PER_HOUR);
//		
//		/* round totalTime */
//		totalTime *= 100.0;
//		long roundedTime = Math.round(totalTime);
//		totalTime = roundedTime / 100.0;
//		
//		/* return result */
//		return totalTime;
		
		int actualhours = 0;
		int actualminutes = 0;
		
		actualminutes = durationMinutes.intValue();
		int tempHours = (int)actualminutes / 60;
		actualminutes = actualminutes % 60;
		actualhours = durationHours.intValue() + tempHours;
		

		String targetMinutesString = "";
		if (actualminutes < 10) {
			targetMinutesString += "0";
		}

		return String.valueOf(actualhours) + ":" + targetMinutesString
				+ String.valueOf(actualminutes);
	}	

}
