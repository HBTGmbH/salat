package org.tb.bdom;

import static javax.persistence.TemporalType.DATE;

import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;
import org.tb.GlobalConstants;

import javax.persistence.Entity;
import javax.persistence.*;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import org.tb.util.DateUtils;

@Data
@Entity
@EqualsAndHashCode(of = {}, callSuper = true)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Employeeorder extends EditDetails implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "SUBORDER_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Suborder suborder;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Employeecontract employeecontract;

    @OneToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEEORDERCONTENT_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Employeeordercontent employeeOrderContent;

    /**
     * sign of the employee order
     */
    private String sign;
    private Double debithours;
    private Byte debithoursunit;
    @Temporal(DATE)
    private Date fromDate;
    @Temporal(DATE)
    private Date untilDate;

    public boolean getOpenEnd() {
        return getUntilDate() == null;
    }

    public Date getUntilDate() {
        if (untilDate == null) {
            return suborder.getUntilDate();
        }
        return untilDate;
    }

    public boolean isValidAt(java.util.Date date) {
        return !date.before(getFromDate()) && (getUntilDate() == null || !date.after(getUntilDate()));
    }

    public String getEmployeeOrderAsString() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        if (getUntilDate() != null) {
            return "EO[" + getEmployeecontract().getEmployee().getSign() + " | "
                    + getSuborder().getCustomerorder().getSign() + " / "
                    + getSuborder().getSign() + " | "
                    + simpleDateFormat.format(getFromDate()) + " - "
                    + simpleDateFormat.format(getUntilDate()) + "]";
        } else {
            return "EO[" + getEmployeecontract().getEmployee().getSign() + " | "
                    + getSuborder().getCustomerorder().getSign() + " / "
                    + getSuborder().getSign() + " | "
                    + simpleDateFormat.format(getFromDate()) + " - offen]";
        }
    }

    /**
     * @return Returns true, if the {@link Employeeorder} is currently valid, false otherwise.
     */
    public boolean getCurrentlyValid() {
        Date today = DateUtils.today();
        return !today.before(getFromDate()) && (getUntilDate() == null || !today.after(getUntilDate()));
    }

    /**
     * @return Returns true, if {@link Employeeorder} fits to its superior objects, false otherwise
     */
    public boolean getFitsToSuperiorObjects() {
        // check from date
        if (getFromDate().before(employeecontract.getValidFrom()) || getFromDate().before(suborder.getFromDate())) {
            return false;
        }

        // check until date
        if (getUntilDate() == null && (employeecontract.getValidUntil() != null || suborder.getUntilDate() != null)) {
            return false;
        }
        return getUntilDate() == null ||
                ((employeecontract.getValidUntil() == null ||
                        !getUntilDate().after(employeecontract.getValidUntil())) &&
                        (suborder.getUntilDate() == null ||
                                !getUntilDate().after(suborder.getUntilDate())));
    }

}
