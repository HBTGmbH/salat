package org.tb.order.viewhelper;

import static org.tb.common.GlobalConstants.DEBITHOURS_UNIT_TOTALTIME;
import static org.tb.common.GlobalConstants.YESNO_NO;
import static org.tb.common.GlobalConstants.YESNO_YES;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.Delegate;
import org.tb.order.domain.Suborder;
import org.tb.order.service.SuborderService;

public class SuborderViewDecorator extends Suborder {
    private static final long serialVersionUID = 1L; // 123L;

    @Delegate
    private final Suborder suborder;
    private Duration duration;
    private Duration durationNotInvoiceable;

    public SuborderViewDecorator(SuborderService suborderService, Suborder suborder) {
        this.suborder = suborder;
        if (suborder.getInvoice() == YESNO_NO) {
            this.duration = Duration.ZERO;
            this.durationNotInvoiceable = Duration.ZERO;
        } else {
            List<Long> descendants = new ArrayList<>();
            generateListOfDescendants(suborder, true, descendants);
            this.duration = suborderService.getTotalDuration(descendants);
            descendants.clear();
            generateListOfDescendants(suborder, false, descendants);
            this.durationNotInvoiceable = suborderService.getTotalDuration(descendants);
        }
    }

    private static void generateListOfDescendants(Suborder so, boolean isInvoiceable, List<Long> listOfDescendents) {
        if (isInvoiceable != (so.getInvoice() == YESNO_YES)) {
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
        return duration;
    }

    public Duration getDurationNotInvoiceable() {
        return durationNotInvoiceable;
    }

    @Override
    public boolean equals (Object obj){
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        SuborderViewDecorator that = (SuborderViewDecorator) obj;
        return suborder.equals(that.suborder);
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
