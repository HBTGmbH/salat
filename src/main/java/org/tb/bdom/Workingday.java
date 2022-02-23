package org.tb.bdom;

import static java.lang.Math.max;
import static javax.persistence.TemporalType.DATE;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Temporal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

@Getter
@Setter
@Entity
public class Workingday extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    private Employeecontract employeecontract;

    @Temporal(DATE)
    private Date refday;
    private int Starttimehour;
    private int Starttimeminute;
    private int breakhours;
    private int breakminutes;

    public int getStarttimehour() {
        return max(Starttimehour, 6);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Workingday) {
            Workingday other = (Workingday) obj;
            return refday.equals(other.refday) &&
                employeecontract.getId() == other.getEmployeecontract().getId();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return refday.hashCode() +
                employeecontract.hashCode();
    }

}
