package org.tb.order.viewhelper;

import java.time.Duration;
import lombok.experimental.Delegate;
import org.tb.common.GlobalConstants;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.EmployeeorderService;

public class EmployeeOrderViewDecorator extends Employeeorder {
    private static final long serialVersionUID = 1L; // 789L;

    @Delegate
    private final Employeeorder employeeOrder;

    private final Duration duration;

    public EmployeeOrderViewDecorator(EmployeeorderService employeeorderService, Employeeorder employeeOrder) {
        this.employeeOrder = employeeOrder;
        this.duration = employeeorderService.getTotalDuration(employeeOrder.getId());
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