package org.tb.bdom;

import static javax.persistence.TemporalType.DATE;
import static org.tb.util.DateUtils.format;

import java.io.Serializable;
import java.time.LocalDate;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Temporal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.util.DateUtils;

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

    @OneToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEEORDERCONTENT_ID")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Employeeordercontent employeeOrderContent;

    /**
     * sign of the employee order
     */
    private String sign;
    private Double debithours;
    private Byte debithoursunit;
    @Temporal(DATE)
    private LocalDate fromDate;
    @Temporal(DATE)
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

}
