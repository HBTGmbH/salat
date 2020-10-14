package org.tb.bdom;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.tb.GlobalConstants;
import org.tb.persistence.TimereportDAO;

@RequiredArgsConstructor
public class CustomerOrderViewDecorator extends Customerorder {
    private static final long serialVersionUID = 1L; // 456L;

    private final TimereportDAO timereportDAO;
    @Delegate
    private final Customerorder customerOrder;

    public Double getDifference() {
        if ((this.customerOrder.getDebithours() != null && this.customerOrder.getDebithours() > 0.0)
                && (this.customerOrder.getDebithoursunit() == null || this.customerOrder.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {
            double rounded, notRounded;
            notRounded = (this.customerOrder.getDebithours() - getDuration());
            rounded = Math.round(notRounded * 100) / 100.0;

            return rounded;
        } else {
            return null;
        }
    }

    public double getDuration() {
        long durationMinutes = timereportDAO.getTotalDurationMinutesForCustomerOrder(customerOrder.getId());

        double totalTime = (double) durationMinutes / GlobalConstants.MINUTES_PER_HOUR;

        /* round totalTime */
        totalTime *= 100.0;
        long roundedTime = Math.round(totalTime);
        totalTime = roundedTime / 100.0;

        /* return result */
        return totalTime;
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
