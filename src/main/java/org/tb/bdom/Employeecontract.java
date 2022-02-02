package org.tb.bdom;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;
import org.tb.GlobalConstants;

import javax.persistence.Entity;
import javax.persistence.*;
import java.io.Serializable;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

@Data
@Entity
@EqualsAndHashCode(of = "id", callSuper = false)
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Employeecontract extends EditDetails implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id;

    @ManyToOne
    @Fetch(FetchMode.SELECT)
    @JoinColumn(name = "SUPERVISOR_ID")
    @Cascade(value = {CascadeType.SAVE_UPDATE})
    private Employee supervisor;
    private Date validFrom;
    private Date validUntil;
    private Double dailyWorkingTime;
    private Boolean freelancer;
    private String taskDescription;
    private Date fixedUntil;
    private java.sql.Date reportAcceptanceDate;
    private java.sql.Date reportReleaseDate;
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
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        if (reportAcceptanceDate != null) {
            return simpleDateFormat.format(reportAcceptanceDate);
        } else {
            return simpleDateFormat.format(validFrom);
        }
    }

    public String getReportReleaseDateString() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        if (reportReleaseDate != null) {
            return simpleDateFormat.format(reportReleaseDate);
        } else {
            return simpleDateFormat.format(validFrom);
        }
    }

    public String getTimeString() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        if (validUntil == null) {
            return simpleDateFormat.format(validFrom) + " - ";
        }
        return simpleDateFormat.format(validFrom) + " - " + simpleDateFormat.format(validUntil);
    }

    public Boolean getOpenEnd() {
        return validUntil == null;
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
        GregorianCalendar calendar = new GregorianCalendar();
        Date release = getReportReleaseDate();

        if (release == null) {
            // new contract without initial login
            return false;
        }

        java.util.Date now = new java.util.Date();
        int month = calendar.get(Calendar.MONTH);
        int year = calendar.get(Calendar.YEAR);

        if (month == 0) {
            year--;
            month = 11;
        } else {
            month--;
        }
        calendar.clear();
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.YEAR, year);
        int date = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        calendar.set(Calendar.DATE, date);
        now = calendar.getTime();

        if (release.before(now)) {
            releaseWarning = true;
        }
        return releaseWarning;
    }

    /**
     * Checks, if the employeecontract is accepted until the last day of the preceding month.
     *
     * @return Returns true, if the contract is not accepted until the last day of the preceding month, false otherwise.
     */
    public boolean getAcceptanceWarning() {
        boolean acceptanceWarning = false;
        GregorianCalendar calendar = new GregorianCalendar();
        Date acceptance = getReportAcceptanceDate();

        if (acceptance == null) {
            // new contract without initial login
            return false;
        }

        java.util.Date now = new java.util.Date();
        int month = calendar.get(Calendar.MONTH);
        int year = calendar.get(Calendar.YEAR);

        if (month == 0) {
            year--;
            month = 11;
        } else {
            month--;
        }
        calendar.clear();
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.YEAR, year);
        int date = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        calendar.set(Calendar.DATE, date);
        now = calendar.getTime();

        if (acceptance.before(now)) {
            acceptanceWarning = true;
        }
        return acceptanceWarning;
    }

    /**
     * @return Returns true, if the {@link Employeecontract} is currently valid, false otherwise.
     */
    public boolean getCurrentlyValid() {
        java.util.Date now = new java.util.Date();
        return !now.before(getValidFrom()) && (getValidUntil() == null || !now.after(getValidUntil()));
    }

    /**
     * Checks, if the employeecontract is accepted until the last day of the preceding month.
     *
     * @return Returns true, if the contract is not accepted until the last day of the preceding month, false otherwise.
     */
    public boolean getAcceptanceWarningByDate(java.util.Date now) {
        boolean acceptanceWarning = false;
        GregorianCalendar calendar = new GregorianCalendar();
        Date acceptance = getReportAcceptanceDate();

        if (acceptance == null) {
            // new contract without initial login
            acceptance = validFrom;
        }
        calendar.setTime(now);
        int month = calendar.get(Calendar.MONTH);
        int year = calendar.get(Calendar.YEAR);

        if (month == 0) {
            year--;
            month = 11;
        } else {
            month--;
        }
        calendar.clear();
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.YEAR, year);
        int date = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        calendar.set(Calendar.DATE, date);
        now = calendar.getTime();

        if (acceptance.before(now)) {
            acceptanceWarning = true;
        }
        return acceptanceWarning;
    }

}
