package org.tb.employee.domain;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.tb.common.util.DateUtils.format;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.tb.common.LocalDateRange;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.domain.DurationMinutesConverter;
import org.tb.common.util.DateUtils;

/**
 * The duration fields have their entity attribute names with minutes to indicate the value in the database.
 * In the Java object world, their getters/setter are named without the minutes ending, because Duration
 * objects are not minutes in reality.
 */
@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Employeecontract extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "employeecontract_supervisor",
        joinColumns = @JoinColumn(name = "EMPLOYEECONTRACT_ID"),
        inverseJoinColumns = @JoinColumn(name = "SUPERVISOR_ID"))
    @Fetch(FetchMode.SELECT)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Employee> supervisors = new ArrayList<>();

    private LocalDate validFrom;
    private LocalDate validUntil;

    @Convert(converter = DurationMinutesConverter.class)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private Duration dailyWorkingTimeMinutes = Duration.ZERO;

    private Boolean freelancer;
    private String taskDescription;
    private LocalDate fixedUntil;
    private LocalDate reportAcceptanceDate;
    private LocalDate reportReleaseDate;
    @Getter(AccessLevel.NONE)
    private Boolean hide;

    /** static overtime ranging from begin of employeecontract to reportAcceptanceDate */
    @Convert(converter = DurationMinutesConverter.class)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private Duration overtimeStaticMinutes = Duration.ZERO;

    @Fetch(FetchMode.SELECT)
    @ManyToOne
    @JoinColumn(name = "EMPLOYEE_ID")
    private Employee employee;

    /** Urlaubstage pro Jahr; der anteilige Anspruch eines Jahres folgt daraus (#1077) */
    @Column(nullable = false)
    private int vacationEntitlement;

    public Boolean getHide() {
        return hide != null && hide;
    }

    public Boolean getFreelancer() {
        if (freelancer == null) {
            freelancer = false;
        }
        return freelancer;
    }

    public String getReportAcceptanceDateString() {
        if (reportAcceptanceDate != null) {
            return format(reportAcceptanceDate);
        } else {
            return "";
        }
    }

    public String getReportReleaseDateString() {
        if (reportReleaseDate != null) {
            return format(reportReleaseDate);
        } else {
            return "";
        }
    }

    public String getTimeString() {
        if (validUntil == null) {
            return format(validFrom) + " - ";
        }
        return format(validFrom) + " - " + format(validUntil);
    }

    public boolean getOpenEnd() {
        return validUntil == null;
    }

    public boolean isValidAt(LocalDate date) {
        return !date.isBefore(validFrom) && (validUntil == null || !date.isAfter(validUntil));
    }

    public boolean isPast() {
        return validUntil != null && validUntil.isBefore(DateUtils.today());
    }

    public LocalDateRange getValidity() {
        return new LocalDateRange(getValidFrom(), getValidUntil());
    }

    /**
     * Der Urlaubsanspruch des Jahres als Zeit — {@code Urlaubstage × Tagesarbeitszeit}, anteilig
     * gekürzt, soweit der Vertrag das Jahr nicht abdeckt. Ein angefangener Monat zählt zu dem
     * Anteil, den er an seinen Tagen hat; ganze Monate zu einem Zwölftel.
     * <p>
     * Die Rechnung hing bis #1077 an einer eigenen Entity {@code Vacation} je Vertrag und Jahr.
     * Zustand war daran nichts: sie ist eine Funktion aus Gültigkeit, Tagesarbeitszeit, Anspruch
     * und dem gefragten Jahr.
     */
    public Duration getEffectiveVacationEntitlement(Year year) {
        LocalDate begin = year.atDay(1);
        LocalDate end = begin.with(TemporalAdjusters.lastDayOfYear());
        // if year is before employee contract validity return ZERO
        if (validFrom.isAfter(end)) {
            return Duration.ZERO;
        }
        // if year is after employee contract validity return ZERO
        if (validUntil != null && validUntil.isBefore(begin)) {
            return Duration.ZERO;
        }
        // return full entitlement if year is covered fully
        if (!validFrom.isAfter(begin) && (validUntil == null || !validUntil.isBefore(end))) {
            return getDailyWorkingTime().multipliedBy(vacationEntitlement);
        }

        // else return partial entitlement
        Duration effectiveEntitlement = Duration.ZERO;
        LocalDate from = DateUtils.max(validFrom, begin);
        LocalDate until = DateUtils.min(validUntil, end);

        // 1. if first month is partial, calc partial of 1/12th entitlement
        if (!from.equals(from.with(TemporalAdjusters.firstDayOfMonth()))) {
            effectiveEntitlement = effectiveEntitlement.plus(partialMonth(
                from,
                // plus 1 day because second date is exclusive
                from.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1)
            ));
            from = from.with(TemporalAdjusters.firstDayOfNextMonth());
        }

        // 2. if last month is partial, calc partial of 1/12th entitlement
        if (!until.equals(until.with(TemporalAdjusters.lastDayOfMonth()))) {
            effectiveEntitlement = effectiveEntitlement.plus(partialMonth(
                until.with(TemporalAdjusters.firstDayOfMonth()),
                // plus 1 day because second date is exclusive
                until.plusDays(1)
            ));
            until = until.with(TemporalAdjusters.firstDayOfMonth()).minusDays(1);
        }

        // 3. calc full month
        var monthCount = 0;
        do {
            monthCount++;
            from = from.plusMonths(1);
        } while (from.isBefore(until));

        return effectiveEntitlement.plus(
            getDailyWorkingTime()
                .multipliedBy(monthCount)
                .multipliedBy(vacationEntitlement)
                .dividedBy(12)
        );
    }

    private Duration partialMonth(LocalDate from, LocalDate untilExclusive) {
        var actualDays = DAYS.between(from, untilExclusive);
        var maxDays = DAYS.between(
            from.with(TemporalAdjusters.firstDayOfMonth()),
            // plus 1 day because second date is exclusive
            from.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1)
        );
        return getDailyWorkingTime()
            .multipliedBy(actualDays)
            .dividedBy(maxDays)
            .multipliedBy(vacationEntitlement)
            .dividedBy(12);
    }

    /**
     * Checks, if the employeecontract is released until the last day of the preceding month.
     *
     * @return Returns true, if the contract is not released until the last day of the preceding month, false otherwise.
     */
    public boolean getReleaseWarning() {
        LocalDate release = getReportReleaseDate();

        if (release == null) {
            // new contract
            return false;
        }

        return release.isBefore(dueUntil());
    }

    /**
     * Bis wann Freigabe und Abnahme reichen müssen: bis zum Ende des Vormonats, bei einem
     * beendeten Vertrag aber nur bis zu seinem letzten Tag (#324). Sonst stünde ein vollständig
     * abgenommener Vertrag für immer als überfällig da.
     */
    private LocalDate dueUntil() {
        return DateUtils.min(DateUtils.getEndOfMonth(DateUtils.addMonths(DateUtils.today(), -1)), validUntil);
    }

    /**
     * FIXME move to service
     * Checks, if the employeecontract is accepted until the last day of the preceding month.
     *
     * @return Returns true, if the contract is not accepted until the last day of the preceding month, false otherwise.
     */
    public boolean getAcceptanceWarning() {
        LocalDate acceptance = getReportAcceptanceDate();

        if (acceptance == null) {
            // new contract
            return false;
        }

        return acceptance.isBefore(dueUntil());
    }

    /**
     * @return Returns true, if the {@link Employeecontract} is currently valid, false otherwise.
     */
    public boolean getCurrentlyValid() {
        LocalDate now = DateUtils.today();
        return getValidUntil() == null || !now.isAfter(getValidUntil());
    }

    /**
     * FIXME move to service
     * Checks, if the employeecontract is accepted until the last day of the preceding month.
     *
     * @return Returns true, if the contract is not accepted until the last day of the preceding month, false otherwise.
     */
    public boolean getAcceptanceWarningByDate(LocalDate date) {
        boolean acceptanceWarning = false;
        LocalDate acceptance = getReportAcceptanceDate();

        if (acceptance == null) {
            // new contract
            acceptance = validFrom;
        }

        LocalDate endOfPreviousMonth = DateUtils.getEndOfMonth(DateUtils.addMonths(date, -1));
        if (acceptance.isBefore(endOfPreviousMonth)) {
            acceptanceWarning = true;
        }
        return acceptanceWarning;
    }

    public Duration getDailyWorkingTime() {
        return dailyWorkingTimeMinutes;
    }

    public void setDailyWorkingTime(Duration value) {
        this.dailyWorkingTimeMinutes = value;
    }

    public Duration getOvertimeStatic() {
        return overtimeStaticMinutes;
    }

    public void setOvertimeStatic(Duration overtimeStaticMinutes) {
        this.overtimeStaticMinutes = overtimeStaticMinutes;
    }

    public boolean overlaps(Employeecontract other) {
        if(this.validUntil == null && other.validUntil == null) {
            return true;
        }
        if(this.validUntil == null && other.validUntil != null) {
            return !other.validUntil.isBefore(validFrom);
        }
        if(this.validUntil != null && other.validUntil == null) {
            return !validUntil.isBefore(other.validFrom);
        }
        // validUntil != null && other.validUntil != null
        if(validFrom.isBefore(other.validFrom)) {
            return !validUntil.isBefore(other.validFrom);
        }
        if(other.validFrom.isBefore(validFrom)) {
            return !other.validUntil.isBefore(validFrom);
        }
        // validFrom == other.validFrom
        return true;
    }

}
