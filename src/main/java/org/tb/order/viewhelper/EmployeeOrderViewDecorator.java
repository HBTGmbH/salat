package org.tb.order.viewhelper;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.tb.common.GlobalConstants;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.order.domain.Employeeorder;

@RequiredArgsConstructor
public class EmployeeOrderViewDecorator extends Employeeorder {
    private static final long serialVersionUID = 1L; // 789L;

    private final TimereportDAO timereportDAO;
    @Delegate
    private final Employeeorder employeeOrder;

    public Duration getDuration() {
        long durationMinutes = timereportDAO.getTotalDurationMinutesForEmployeeOrder(employeeOrder.getId());
        return Duration.ofMinutes(durationMinutes);
    }

    public Duration getDifference() {
        if ((this.employeeOrder.getDebithours() != null && this.employeeOrder.getDebithours().toMinutes() > 0)
                && (this.employeeOrder.getDebithoursunit() == null || this.employeeOrder.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {
            return this.employeeOrder.getDebithours().minus(getDuration());
        } else {
            return null;
        }
    }

}
