package org.tb.order.viewhelper;

import static org.tb.common.GlobalConstants.DEBITHOURS_UNIT_TOTALTIME;

import java.time.Duration;
import lombok.experimental.Delegate;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Customerorder;

public class CustomerOrderViewDecorator extends Customerorder {
    private static final long serialVersionUID = 1L; // 456L;

    @Delegate
    private final Customerorder customerOrder;
    private final Duration duration;

    public CustomerOrderViewDecorator(TimereportService timereportService, Customerorder customerOrder) {
        long durationMinutes = timereportService.getTotalDurationMinutesForCustomerOrder(customerOrder.getId());
        this.duration = Duration.ofMinutes(durationMinutes);
        this.customerOrder = customerOrder;
    }

    public Duration getDifference() {
        if (this.customerOrder.getDebithours() != null
            && !this.customerOrder.getDebithours().isZero()
            && (this.customerOrder.getDebithoursunit() == null || this.customerOrder.getDebithoursunit() == DEBITHOURS_UNIT_TOTALTIME)) {
            return this.customerOrder.getDebithours().minus(getDuration());
        } else {
            return null;
        }
    }

    public Duration getDuration() {
        return duration;
    }

    @Override
    public boolean equals (Object obj){
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        CustomerOrderViewDecorator that = (CustomerOrderViewDecorator) obj;
        return customerOrder.equals(that.customerOrder);
    }

    @Override
    public int hashCode() {
        return customerOrder.hashCode();
    }

}
