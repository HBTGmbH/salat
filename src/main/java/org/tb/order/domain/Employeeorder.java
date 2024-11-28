package org.tb.order.domain;

import static org.tb.common.util.DateUtils.format;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.common.DateRange;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.domain.DurationMinutesConverter;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.Employeecontract;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Employeeorder extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "SUBORDER_ID")
    @Cascade(CascadeType.PERSIST)
    private Suborder suborder;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    @Cascade(CascadeType.PERSIST)
    private Employeecontract employeecontract;

    /**
     * sign of the employee order
     */
    private String sign;

    @Convert(converter = DurationMinutesConverter.class)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Duration debitMinutes = Duration.ZERO;

    private Byte debithoursunit;
    private LocalDate fromDate;
    private LocalDate untilDate;

    public boolean getOpenEnd() {
        return getUntilDate() == null;
    }

    public LocalDate getEffectiveUntilDate() {
        var suborderDate = suborder.getEffectiveUntilDate();
        var employeecontractDate = employeecontract.getValidUntil();
        if(untilDate == null && employeecontractDate == null && suborderDate == null) {
            return null;
        }
        if(untilDate != null && employeecontractDate != null && suborderDate != null) {
            return DateUtils.min(untilDate, DateUtils.min(suborderDate, employeecontractDate));
        }
        if(untilDate != null && employeecontractDate != null) {
            return DateUtils.min(untilDate, employeecontractDate);
        }
        if(untilDate != null && suborderDate != null) {
            return DateUtils.min(untilDate, suborderDate);
        }
        if(employeecontractDate != null && suborderDate != null) {
            return DateUtils.min(suborderDate, employeecontractDate);
        }
        if(suborderDate != null) {
            return suborderDate;
        }
        if(employeecontractDate != null) {
            return employeecontractDate;
        }
        return untilDate;
    }

    public boolean isValidAt(java.time.LocalDate date) {
        return !date.isBefore(getFromDate()) && (getEffectiveUntilDate() == null || !date.isAfter(getEffectiveUntilDate()));
    }

    public String getEmployeeOrderAsString() {
        if (getUntilDate() != null) {
            return "EO[" + getEmployeecontract().getEmployee().getSign() + " | "
                    + getSuborder().getCustomerorder().getSign() + " / "
                    + getSuborder().getCompleteOrderSign() + " | "
                    + format(getFromDate()) + " - "
                    + format(getUntilDate()) + "]";
        } else {
            return "EO[" + getEmployeecontract().getEmployee().getSign() + " | "
                    + getSuborder().getCustomerorder().getSign() + " / "
                    + getSuborder().getCompleteOrderSign() + " | "
                    + format(getFromDate()) + " - offen]";
        }
    }

    public DateRange getValidity() {
        return new DateRange(getFromDate(), getUntilDate());
    }

    /**
     * @return Returns true, if the {@link Employeeorder} is currently valid, false otherwise.
     */
    public boolean getCurrentlyValid() {
        LocalDate today = DateUtils.today();
        return !today.isBefore(getFromDate()) && (getEffectiveUntilDate() == null || !today.isAfter(getEffectiveUntilDate()));
    }

    /**
     * @return Returns true, if {@link Employeeorder} fits to its superior objects, false otherwise
     */
    public boolean getFitsToSuperiorObjects() {
        // check from date
        if (getFromDate().isBefore(employeecontract.getValidFrom()) || getFromDate().isBefore(suborder.getFromDate())) {
            return false;
        }

        // check until date
        if (getUntilDate() == null && (employeecontract.getValidUntil() != null || suborder.getUntilDate() != null)) {
            return false;
        }
        return getUntilDate() == null ||
                ((employeecontract.getValidUntil() == null ||
                        !getUntilDate().isAfter(employeecontract.getValidUntil())) &&
                        (suborder.getUntilDate() == null ||
                                !getUntilDate().isAfter(suborder.getUntilDate())));
    }

    public Duration getDebithours() {
        return debitMinutes; // its a Duration - hours or minutes make no difference
    }

    public void setDebithours(Duration value) {
        // its a Duration - hours or minutes make no difference
        debitMinutes = value;
    }

    public boolean overlaps(Employeeorder other) {
        if(this.untilDate == null && other.untilDate == null) {
            return true;
        }
        if(this.untilDate == null && other.untilDate != null) {
            return !other.untilDate.isBefore(fromDate);
        }
        if(this.untilDate != null && other.untilDate == null) {
            return !untilDate.isBefore(other.fromDate);
        }
        // untilDate != null && other.untilDate != null
        if(fromDate.isBefore(other.fromDate)) {
            return !untilDate.isBefore(other.fromDate);
        }
        if(other.fromDate.isBefore(fromDate)) {
            return !other.untilDate.isBefore(fromDate);
        }
        // fromDate == other.fromDate
        return true;
    }

}
