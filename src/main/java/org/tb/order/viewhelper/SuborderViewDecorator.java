package org.tb.order.viewhelper;

import static org.tb.common.GlobalConstants.DEBITHOURS_UNIT_TOTALTIME;
import static org.tb.common.GlobalConstants.SUBORDER_INVOICE_NO;
import static org.tb.common.GlobalConstants.SUBORDER_INVOICE_YES;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.order.domain.Suborder;

@RequiredArgsConstructor
public class SuborderViewDecorator extends Suborder {
    private static final long serialVersionUID = 1L; // 123L;

    private final TimereportDAO timereportDAO;
    @Delegate
    private final Suborder suborder;
    private Duration duration;
    private Duration durationNotInvoiceable;

    private static void generateListOfDescendants(Suborder so, boolean isInvoiceable, List<Long> listOfDescendents) {
        if (isInvoiceable != (so.getInvoice() == SUBORDER_INVOICE_YES)) {
            return;
        }
        listOfDescendents.add(so.getId());
        if (so.getSuborders() != null) {
            for (Suborder child : so.getSuborders()) {
                generateListOfDescendants(child, isInvoiceable, listOfDescendents);
            }
        }
    }

    public Duration getDifference() {
        if (suborder.getDebithours() != null
            && suborder.getDebithours().toMinutes() > 0
            && (suborder.getDebithoursunit() == null || suborder.getDebithoursunit() == DEBITHOURS_UNIT_TOTALTIME)) {
            return suborder.getDebithours().minus(getDuration());
        } else {
            return null;
        }
    }

    public Duration getDuration() {
        if (duration == null) {
            if (suborder.getInvoice() == SUBORDER_INVOICE_NO) {
                return Duration.ZERO;
            }

            List<Long> descendants = new ArrayList<>();
            generateListOfDescendants(suborder, true, descendants);

            long durationMinutes = timereportDAO.getTotalDurationMinutesForSuborders(descendants);
            duration = Duration.ofMinutes(durationMinutes);
        }
        return duration;
    }

    public Duration getDurationNotInvoiceable() {
        if (durationNotInvoiceable == null) {
            if (suborder.getInvoice() == SUBORDER_INVOICE_YES) {
                return Duration.ZERO;
            }

            List<Long> descendants = new ArrayList<>();
            generateListOfDescendants(suborder, false, descendants);

            long durationMinutes = timereportDAO.getTotalDurationMinutesForSuborders(descendants);
            duration = Duration.ofMinutes(durationMinutes);
        }
        return durationNotInvoiceable;
    }

    @Override
    public boolean equals(Object obj) {
        return suborder.equals(obj);
    }

    @Override
    public int hashCode() {
        return suborder.hashCode();
    }

    @Override
    public String toString() {
        return suborder.toString();
    }

}
