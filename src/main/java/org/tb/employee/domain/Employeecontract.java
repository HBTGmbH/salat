package org.tb.employee.domain;

import static org.tb.common.util.DateUtils.format;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
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
import org.tb.dailyreport.domain.Vacation;
import org.tb.order.domain.Employeeorder;

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

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "SUPERVISOR_ID")
    @Cascade(CascadeType.SAVE_UPDATE)
    private Employee supervisor;

    private LocalDate validFrom;
    private LocalDate validUntil;

    @Convert(converter = DurationMinutesConverter.class)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Duration dailyWorkingTimeMinutes = Duration.ZERO;

    private Boolean freelancer;
    private String taskDescription;
    private LocalDate fixedUntil;
    private LocalDate reportAcceptanceDate;
    private LocalDate reportReleaseDate;
    private Boolean hide;

    /** static overtime ranging from begin of employeecontract to reportAcceptanceDate */
    @Convert(converter = DurationMinutesConverter.class)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Duration overtimeStaticMinutes = Duration.ZERO;

    @Fetch(FetchMode.SELECT)
    @ManyToOne
    @JoinColumn(name = "EMPLOYEE_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Employee employee;

    /**
     * list of employeeorders, associated to this employeecontract
     */
    @OneToMany(mappedBy = "employeecontract")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Employeeorder> employeeorders = new ArrayList<>();

    /**
     * list of vacations, associated to this employeecontract
     */
    @OneToMany(mappedBy = "employeecontract")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Vacation> vacations = new ArrayList<>();



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

    public int getVacationEntitlement() {
        if(vacations == null) return 0;
        return vacations.stream().findAny().map(Vacation::getEntitlement).orElse(0);
    }

    public Duration getEffectiveVacationEntitlement(int year) {
        if(vacations == null) return Duration.ZERO;
        return vacations.stream()
            .filter(v -> v.getYear() == year)
            .map(Vacation::getEffectiveEntitlement)
            .findAny()
            .orElse(Duration.ZERO);
    }

    public Optional<Vacation> getVacation(int year) {
        if(vacations == null) return Optional.empty();
        return vacations.stream()
            .filter(v -> v.getYear() == year)
            .findAny();
    }

    /**
     * Checks, if the employeecontract is released until the last day of the preceding month.
     *
     * @return Returns true, if the contract is not released until the last day of the preceding month, false otherwise.
     */
    public boolean getReleaseWarning() {
        boolean releaseWarning = false;
        LocalDate release = getReportReleaseDate();

        if (release == null) {
            // new contract
            return false;
        }

        LocalDate endOfPreviousMonth = DateUtils.getEndOfMonth(DateUtils.addMonths(DateUtils.today(), -1));
        if (release.isBefore(endOfPreviousMonth)) {
            releaseWarning = true;
        }
        return releaseWarning;
    }

    /**
     * FIXME move to service
     * Checks, if the employeecontract is accepted until the last day of the preceding month.
     *
     * @return Returns true, if the contract is not accepted until the last day of the preceding month, false otherwise.
     */
    public boolean getAcceptanceWarning() {
        boolean acceptanceWarning = false;
        LocalDate acceptance = getReportAcceptanceDate();

        if (acceptance == null) {
            // new contract
            return false;
        }

        LocalDate endOfPreviousMonth = DateUtils.getEndOfMonth(DateUtils.addMonths(DateUtils.today(), -1));
        if (acceptance.isBefore(endOfPreviousMonth)) {
            acceptanceWarning = true;
        }
        return acceptanceWarning;
    }

    /**
     * @return Returns true, if the {@link Employeecontract} is currently valid, false otherwise.
     */
    public boolean getCurrentlyValid() {
        LocalDate now = DateUtils.today();
        return !now.isBefore(getValidFrom()) &&
            (getValidUntil() == null || !now.isAfter(getValidUntil()));
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
