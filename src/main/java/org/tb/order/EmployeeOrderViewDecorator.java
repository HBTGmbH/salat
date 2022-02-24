package org.tb.order;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.tb.common.GlobalConstants;
import org.tb.dailyreport.TimereportDAO;

@RequiredArgsConstructor
public class EmployeeOrderViewDecorator extends Employeeorder {
    private static final long serialVersionUID = 1L; // 789L;

    private final TimereportDAO timereportDAO;
    @Delegate
    private final Employeeorder employeeOrder;

    public double getDuration() {
        long durationMinutes = timereportDAO.getTotalDurationMinutesForEmployeeOrder(employeeOrder.getId());

        double totalTime = (double) durationMinutes / GlobalConstants.MINUTES_PER_HOUR;

        /* round totalTime */
        totalTime *= 100.0;
        long roundedTime = Math.round(totalTime);
        totalTime = roundedTime / 100.0;

        /* return result */
        return totalTime;
    }

    public Double getDifference() {
        if ((this.employeeOrder.getDebithours() != null && this.employeeOrder.getDebithours() > 0.0)
                && (this.employeeOrder.getDebithoursunit() == null || this.employeeOrder.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {
            double rounded, notRounded;
            notRounded = (this.employeeOrder.getDebithours() - getDuration());
            rounded = Math.round(notRounded * 100) / 100.0;

            return rounded;
        } else {
            return null;
        }
    }

}
