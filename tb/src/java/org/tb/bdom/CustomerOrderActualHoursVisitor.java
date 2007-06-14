package org.tb.bdom;

import org.tb.GlobalConstants;
import org.tb.persistence.TimereportDAO;

public class CustomerOrderActualHoursVisitor implements SuborderVisitor {

	private TimereportDAO timereportDAO;
	private Long durationHours;
	private Long durationMinutes;
	
	public CustomerOrderActualHoursVisitor(TimereportDAO timereportDAO) {
		this.durationHours = 0l;
		this.durationMinutes = 0l;
		this.timereportDAO = timereportDAO;
	}
	

	public void visitSuborder(Suborder suborder) {
		durationHours += timereportDAO.getTotalDurationHoursForSuborder(suborder.getId());
		durationMinutes += timereportDAO.getTotalDurationMinutesForSuborder(suborder.getId());	
	}

	

	public double getTotalTime() {
		double totalTime = durationHours.doubleValue() + (durationMinutes.doubleValue() / GlobalConstants.MINUTES_PER_HOUR);
		
		/* round totalTime */
		totalTime *= 100.0;
		long roundedTime = Math.round(totalTime);
		totalTime = roundedTime / 100.0;
		
		/* return result */
		return totalTime;
	}
	
}
