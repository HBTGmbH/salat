package org.tb.bdom;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.GlobalConstants;

import javax.persistence.*;
import java.io.Serializable;
import java.text.SimpleDateFormat;

import static java.lang.Math.max;

@Getter
@Setter
@Entity
public class Workingday implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    private Employeecontract employeecontract;
    private java.sql.Date refday;
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
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
            String refdateString = simpleDateFormat.format(refday);
            String otherString = simpleDateFormat.format(other.getRefday());

            return refdateString.equals(otherString) &&
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
