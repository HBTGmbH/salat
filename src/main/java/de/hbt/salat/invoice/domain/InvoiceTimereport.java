package de.hbt.salat.invoice.domain;

import static de.hbt.salat.common.GlobalConstants.MINUTES_PER_HOUR;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.order.domain.SurchargeKind;

@Data
public class InvoiceTimereport implements Serializable {

    private final long id;
    private final long suborderId;
    private final LocalDate referenceDay;
    private final String taskDescription;
    private final String employeeName;
    private final String employeeSign;
    private final Duration duration;
    /** In their stored order, {@code position = 1} first (#1390). */
    private final List<String> ticketReferences;
    /** A night, Saturday, Sunday or holiday assignment (#1469), {@code null} for none. */
    private final SurchargeKind surchargeKind;
    private final Integer surchargePercent;
    /** The time surcharge as the overtime account counts it, none on standby; zero without a flag. */
    private final Duration surchargeCredit;
    /**
     * Whether {@link #duration} already includes {@link #surchargeCredit} (#1470): so when the invoice
     * does not list the surcharges as positions of their own.
     */
    private final boolean surchargeIncluded;
    private boolean visible;

    /** The booking with its booked duration; the surcharges, if any, are listed apart. */
    public InvoiceTimereport(TimereportDTO timereport) {
        this(timereport, false);
    }

    /**
     * @param surchargeIncluded whether the time surcharge counts as part of the duration (#1470) — instead
     *                          of a position of its own
     */
    public InvoiceTimereport(TimereportDTO timereport, boolean surchargeIncluded) {
        this.id = timereport.getId();
        this.suborderId = timereport.getSuborderId();
        this.referenceDay = timereport.getReferenceday();
        this.taskDescription = timereport.getTaskdescription();
        this.employeeName = timereport.getEmployeeName();
        this.employeeSign = timereport.getEmployeeSign();
        this.surchargeKind = timereport.getSurchargeKind();
        this.surchargePercent = timereport.getSurchargePercent();
        this.surchargeCredit = timereport.getCountedSurchargeCredit();
        this.surchargeIncluded = surchargeIncluded;
        this.duration = surchargeIncluded ? timereport.getDurationWithSurcharge() : timereport.getDuration();
        this.ticketReferences = timereport.getTicketReferences() == null ? List.of() : List.copyOf(timereport.getTicketReferences());
        this.visible = true;
    }

    /** Whether the booking earns a time surcharge that counts. */
    public boolean isSurcharged() {
        return surchargeKind != null && surchargeCredit != null && !surchargeCredit.isZero();
    }

    /** The duration as it was booked, without a surcharge included in it. */
    public Duration getBookedDuration() {
        return surchargeIncluded ? duration.minus(surchargeCredit) : duration;
    }

    public BigDecimal getHours() {
        return BigDecimal
            .valueOf(duration.toMinutes())
            .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
    }

}
