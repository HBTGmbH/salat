package org.tb.bdom;

import org.tb.GlobalConstants;
import org.tb.persistence.TimereportDAO;

public class CustomerOrderActualHoursVisitor implements SuborderVisitor {

	private TimereportDAO timereportDAO;
	private Long durationMinutes;
	
	public CustomerOrderActualHoursVisitor(TimereportDAO timereportDAO) {
		this.durationMinutes = 0l;
		this.timereportDAO = timereportDAO;
	}
	

	public void visitSuborder(Suborder suborder) {
		durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId());	
	}

	public double getTotalTime() {
		double totalTime = durationMinutes.doubleValue() / GlobalConstants.MINUTES_PER_HOUR;
		
		/* round totalTime */
		totalTime *= 100.0;
		long roundedTime = Math.round(totalTime);
		totalTime = roundedTime / 100.0;
		
		/* return result */
		return totalTime;
	}	
}
