package org.tb.employee.domain;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.tb.common.util.DateUtils.max;
import static org.tb.common.util.DateUtils.min;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.TemporalAdjusters;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.common.domain.AuditedEntity;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Vacation extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEECONTRACT_ID")
    private Employeecontract employeecontract;

    private Integer year;
    private Integer entitlement;
    private Integer used;

    public Duration getEffectiveEntitlement() {
        var year = Year.of(this.year);
        LocalDate begin = year.atDay(1);
        LocalDate end = begin.with(TemporalAdjusters.lastDayOfYear());
        // if year is before employee contract validity return ZERO
        if(employeecontract.getValidFrom().isAfter(end)) {
            return Duration.ZERO;
        }
        // if year is after employee contract validity return ZERO
        if(employeecontract.getValidUntil() != null && employeecontract.getValidUntil().isBefore(begin)) {
            return Duration.ZERO;
        }
        // return full entitlement if year is covered fully
        if(!employeecontract.getValidFrom().isAfter(begin) && (employeecontract.getValidUntil() == null || !employeecontract.getValidUntil().isBefore(end))) {
            return employeecontract.getDailyWorkingTime().multipliedBy(entitlement);
        }

        // else return partial entitlement
        Duration effectiveEntitlement = Duration.ZERO;
        LocalDate validFrom = max(employeecontract.getValidFrom(), begin);
        LocalDate validUntil = min(employeecontract.getValidUntil(), end);

        // 1. if first month is partial, calc partial of 1/12th entitlement
        if(!validFrom.equals(validFrom.with(TemporalAdjusters.firstDayOfMonth()))) {
            var actualDays = DAYS.between(
                validFrom,
                // plus 1 day because second date is exclusive
                validFrom.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1)
            );
            var maxDays = DAYS.between(
                validFrom.with(TemporalAdjusters.firstDayOfMonth()),
                // plus 1 day because second date is exclusive
                validFrom.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1)
            );
            effectiveEntitlement = effectiveEntitlement.plus(
                employeecontract
                .getDailyWorkingTime()
                .multipliedBy(actualDays)
                .dividedBy(maxDays)
                .multipliedBy(entitlement)
                .dividedBy(12)
            );
            validFrom = validFrom.with(TemporalAdjusters.firstDayOfNextMonth());
        }

        // 2. if last month is partial, calc partial of 1/12th entitlement
        if(!validUntil.equals(validUntil.with(TemporalAdjusters.lastDayOfMonth()))) {
            var actualDays = DAYS.between(
                validUntil.with(TemporalAdjusters.firstDayOfMonth()),
                // plus 1 day because second date is exclusive
                validUntil.plusDays(1)
            );
            var maxDays = DAYS.between(
                validUntil.with(TemporalAdjusters.firstDayOfMonth()),
                // plus 1 day because second date is exclusive
                validUntil.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1)
            );
            effectiveEntitlement = effectiveEntitlement.plus(
                employeecontract
                    .getDailyWorkingTime()
                    .multipliedBy(actualDays)
                    .dividedBy(maxDays)
                    .multipliedBy(entitlement)
                    .dividedBy(12)
            );
            validUntil = validUntil.with(TemporalAdjusters.firstDayOfMonth()).minusDays(1);
        }

        // 3. calc full month
        var monthCount = 0;
        do {
            monthCount++;
            validFrom = validFrom.plusMonths(1);
        } while(validFrom.isBefore(validUntil));
        effectiveEntitlement = effectiveEntitlement.plus(
            employeecontract
                .getDailyWorkingTime()
                .multipliedBy(monthCount)
                .multipliedBy(entitlement)
                .dividedBy(12)
        );

        return effectiveEntitlement;
    }

}
