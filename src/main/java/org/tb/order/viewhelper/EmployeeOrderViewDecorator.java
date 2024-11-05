package org.tb.order.viewhelper;

import java.time.Duration;
import lombok.experimental.Delegate;
import org.tb.common.GlobalConstants;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.order.domain.Employeeorder;

public class EmployeeOrderViewDecorator extends Employeeorder {
    private static final long serialVersionUID = 1L; // 789L;

    @Delegate
    private final Employeeorder employeeOrder;

    private final Duration duration;

    public EmployeeOrderViewDecorator(TimereportDAO timereportDAO, Employeeorder employeeOrder) {
        this.employeeOrder = employeeOrder;
        long durationMinutes = timereportDAO.getTotalDurationMinutesForEmployeeOrder(employeeOrder.getId());
        this.duration = Duration.ofMinutes(durationMinutes);
    }

    public Duration getDuration() {
        return this.duration;
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