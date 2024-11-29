package org.tb.invoice.service;

import static org.tb.common.GlobalConstants.YESNO_YES;
import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.SuborderVisitor;

@RequiredArgsConstructor
public class InvoiceSuborderActualHoursVisitor implements SuborderVisitor {

    private final TimereportService timereportService;
    private final LocalDate fromDate;
    private final LocalDate untilDate;
    private final boolean invoicebox;
    private Long durationMinutes = 0L;

    // FIXME what is this?!?
    public void visitSuborder(Suborder suborder) {
        if (invoicebox && YESNO_YES == suborder.getInvoice()) {
            durationMinutes += timereportService.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
        } else if (invoicebox && YESNO_YES == suborder.getInvoice()) {
            durationMinutes += timereportService.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
        } else if (!invoicebox && YESNO_YES == suborder.getInvoice()) {
            durationMinutes += timereportService.getTotalDurationMinutesForSuborder(suborder.getId(), fromDate, untilDate);
        }
    }

    public String getTotalTime() {
        return timeFormatMinutes(durationMinutes);
    }

    public long getTotalMinutes() {
        return durationMinutes;
    }
}
