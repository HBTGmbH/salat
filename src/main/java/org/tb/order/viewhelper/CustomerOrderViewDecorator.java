package org.tb.order.viewhelper;

import static org.tb.common.GlobalConstants.DEBITHOURS_UNIT_TOTALTIME;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.order.domain.Customerorder;

@RequiredArgsConstructor
public class CustomerOrderViewDecorator extends Customerorder {
    private static final long serialVersionUID = 1L; // 456L;

    private final TimereportDAO timereportDAO;
    @Delegate
    private final Customerorder customerOrder;

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
        long durationMinutes = timereportDAO.getTotalDurationMinutesForCustomerOrder(customerOrder.getId());
        return Duration.ofMinutes(durationMinutes);
    }

    @Override
    public boolean equals(Object obj) {
        return customerOrder.equals(obj);
    }
    @Override
    public int hashCode() {
        return customerOrder.hashCode();
    }

}
