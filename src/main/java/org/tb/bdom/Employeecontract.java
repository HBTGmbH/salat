package org.tb.bdom;

import static javax.persistence.TemporalType.DATE;
import static org.tb.util.DateUtils.format;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
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
import org.tb.GlobalConstants;
import org.tb.util.DateUtils;

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

    @Temporal(DATE)
    private LocalDate validFrom;
    @Temporal(DATE)
    private LocalDate validUntil;
    private Double dailyWorkingTime;
    private Boolean freelancer;
    private String taskDescription;
    @Temporal(DATE)
    private LocalDate fixedUntil;
    @Temporal(DATE)
    private LocalDate reportAcceptanceDate;
    @Temporal(DATE)
    private LocalDate reportReleaseDate;
    private Boolean hide;
    /**
     * static overtime from begin of employeecontract to reportAcceptanceDate
     */
    private double overtimeStatic;
    /**
     * boolean for new overtime computation: if true, overtimeStatic has not been set before
     */
    private Boolean useOvertimeOld;

    @OneToOne
    // FIXME check if ManyToOne?
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "EMPLOYEE_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Employee employee;

    /**
     * list of timereports, associated to this employeecontract
     */
    @OneToMany(mappedBy = "employeecontract")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Timereport> timereports;

    /**
     * list of employeeorders, associated to this employeecontract
     */
    @OneToMany(mappedBy = "employeecontract")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Employeeorder> employeeorders;

    /**
     * list of vacations, associated to this employeecontract
     */
    @OneToMany(mappedBy = "employeecontract")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Vacation> vacations;

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
            return format(validFrom);
        }
    }

    public String getReportReleaseDateString() {
        if (reportReleaseDate != null) {
            return format(reportReleaseDate);
        } else {
            return format(validFrom);
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

    public Integer getVacationEntitlement() {
        if (vacations != null && vacations.size() > 0) {
            // actually, vacation entitlement is a constant value
            // for an employee (not year-dependent), so just take the
            // first vacation entry to get the value
            return vacations.get(0).getEntitlement();
        } else {
            return GlobalConstants.VACATION_PER_YEAR;
        }
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
            // new contract without initial login
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
            // new contract without initial login
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
            // new contract without initial login
            acceptance = validFrom;
        }

        LocalDate endOfPreviousMonth = DateUtils.getEndOfMonth(DateUtils.addMonths(date, -1));
        if (acceptance.isBefore(endOfPreviousMonth)) {
            acceptanceWarning = true;
        }
        return acceptanceWarning;
    }

}
