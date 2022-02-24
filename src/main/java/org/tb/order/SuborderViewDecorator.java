package org.tb.order;

import static org.tb.common.GlobalConstants.SUBORDER_INVOICE_NO;
import static org.tb.common.GlobalConstants.SUBORDER_INVOICE_YES;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.tb.common.GlobalConstants;
import org.tb.dailyreport.TimereportDAO;

@RequiredArgsConstructor
public class SuborderViewDecorator extends Suborder {
    private static final long serialVersionUID = 1L; // 123L;

    private final TimereportDAO timereportDAO;
    @Delegate
    private final Suborder suborder;
    private Double duration;
    private Double durationNotInvoiceable;

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

    public Double getDifference() {
        if (this.suborder.getDebithours() != null && this.suborder.getDebithours() > 0.0
                && (this.suborder.getDebithoursunit() == null || this.suborder.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {
            double rounded, notRounded;
            notRounded = this.suborder.getDebithours() - getDuration();
            rounded = Math.round(notRounded * 100) / 100.0;

            return rounded;
        } else {
            return null;
        }
    }

    public double getDuration() {
        if (this.duration == null) {
            if (suborder.getInvoice() == SUBORDER_INVOICE_NO) {
                return 0;
            }

            List<Long> descendants = new ArrayList<>();
            generateListOfDescendants(suborder, true, descendants);

            this.duration = (double) (timereportDAO.getTotalDurationMinutesForSuborders(descendants) * 100 / GlobalConstants.MINUTES_PER_HOUR) / 100;
        }
        return this.duration;
    }

    public double getDurationNotInvoiceable() {
        if (this.durationNotInvoiceable == null) {
            if (suborder.getInvoice() == SUBORDER_INVOICE_YES) {
                return 0;
            }

            List<Long> descendants = new ArrayList<>();
            generateListOfDescendants(suborder, false, descendants);

            this.durationNotInvoiceable = (double) (timereportDAO.getTotalDurationMinutesForSuborders(descendants) * 100 / GlobalConstants.MINUTES_PER_HOUR) / 100;
        }
        return this.durationNotInvoiceable;
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
