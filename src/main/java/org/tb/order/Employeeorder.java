package org.tb.order;

import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.common.util.DateUtils.format;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.common.AuditedEntity;
import org.tb.common.DurationMinutesConverter;
import org.tb.common.util.DateUtils;
import org.tb.employee.Employeecontract;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Employeeorder extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "SUBORDER_ID")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Suborder suborder;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Employeecontract employeecontract;

    /**
     * sign of the employee order
     */
    private String sign;

    @Convert(converter = DurationMinutesConverter.class)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Duration debitMinutes;

    private Byte debithoursunit;
    private LocalDate fromDate;
    private LocalDate untilDate;

    public boolean getOpenEnd() {
        return getUntilDate() == null;
    }

    public LocalDate getUntilDate() {
        if (untilDate == null) {
            return suborder.getUntilDate();
        }
        return untilDate;
    }

    public boolean isValidAt(java.time.LocalDate date) {
        return !date.isBefore(getFromDate()) && (getUntilDate() == null || !date.isAfter(getUntilDate()));
    }

    public String getEmployeeOrderAsString() {
        if (getUntilDate() != null) {
            return "EO[" + getEmployeecontract().getEmployee().getSign() + " | "
                    + getSuborder().getCustomerorder().getSign() + " / "
                    + getSuborder().getSign() + " | "
                    + format(getFromDate()) + " - "
                    + format(getUntilDate()) + "]";
        } else {
            return "EO[" + getEmployeecontract().getEmployee().getSign() + " | "
                    + getSuborder().getCustomerorder().getSign() + " / "
                    + getSuborder().getSign() + " | "
                    + format(getFromDate()) + " - offen]";
        }
    }

    /**
     * @return Returns true, if the {@link Employeeorder} is currently valid, false otherwise.
     */
    public boolean getCurrentlyValid() {
        LocalDate today = DateUtils.today();
        return !today.isBefore(getFromDate()) && (getUntilDate() == null || !today.isAfter(getUntilDate()));
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

}
