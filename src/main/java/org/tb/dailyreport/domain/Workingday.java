package org.tb.dailyreport.domain;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.common.AuditedEntity;
import org.tb.employee.domain.Employeecontract;

@Getter
@Setter
@Entity
public class Workingday extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    private Employeecontract employeecontract;

    private LocalDate refday;
    private int starttimehour;
    private int starttimeminute;
    private int breakhours;
    private int breakminutes;

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Workingday other) {
            return refday.equals(other.refday) &&
                   Objects.equals(employeecontract.getId(), other.getEmployeecontract().getId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return refday.hashCode() +
                employeecontract.hashCode();
    }

    public LocalDateTime getStartOfWorkingDay() {
        LocalTime localTime = LocalTime.of(starttimehour, starttimeminute);
        return LocalDateTime.of(refday, localTime);
    }

    public Duration getBreakLength() {
        return Duration.ofHours(breakhours).plusMinutes(breakminutes);
    }

    public long getBreakLengthInMinutes() {
        return getBreakLength().toMinutes();
    }
}
