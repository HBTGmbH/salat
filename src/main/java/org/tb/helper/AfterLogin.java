package org.tb.helper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.util.MessageResources;
import org.apache.struts.util.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.bdom.*;
import org.tb.persistence.*;
import org.tb.util.DateUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;

import static org.tb.util.TimeFormatUtils.timeFormatMinutes;

@Component
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class AfterLogin {

    private final TimereportHelper timereportHelper;
    private final EmployeeorderDAO employeeorderDAO;
    private final PublicholidayDAO publicholidayDAO;
    private final TimereportDAO timereportDAO;
    private final StatusReportDAO statusReportDAO;
    private final CustomerorderDAO customerorderDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final SuborderDAO suborderDAO;

    private static final String SYSTEM_SIGN = "system";

    private List<Warning> checkEmployeeorders(Employeecontract employeecontract, MessageResources resources, Locale locale) {
        List<Warning> warnings = new ArrayList<>();

        for (Employeeorder employeeorder : employeeorderDAO.getEmployeeordersForEmployeeordercontentWarning(employeecontract)) {
            if (!employeecontract.getFreelancer() && !employeeorder.getSuborder().getNoEmployeeOrderContent()) {
                try {
                    if (employeeorder.getEmployeeOrderContent() == null) {
                        throw new RuntimeException("null content");
                    } else if (employeeorder.getEmployeeOrderContent() != null && !employeeorder.getEmployeeOrderContent().getCommitted_emp()
                            && employeeorder.getEmployeecontract().getEmployee().equals(employeecontract.getEmployee())) {
                        Warning warning = new Warning();
                        warning.setSort(resources.getMessage(locale, "employeeordercontent.thumbdown.text"));
                        warning.setText(employeeorder.getEmployeeOrderAsString());
                        warning.setLink("/tb/do/ShowEmployeeorder?employeeContractId=" + employeeorder.getEmployeecontract().getId());
                        warnings.add(warning);
                    } else if (employeeorder.getEmployeeOrderContent() != null && !employeeorder.getEmployeeOrderContent().getCommitted_mgmt()
                            && employeeorder.getEmployeeOrderContent().getContactTechHbt().equals(employeecontract.getEmployee())) {
                        Warning warning = new Warning();
                        warning.setSort(resources.getMessage(locale, "employeeordercontent.thumbdown.text"));
                        warning.setText(employeeorder.getEmployeeOrderAsString());
                        warning.setLink("/tb/do/ShowEmployeeorder?employeeContractId=" + employeeorder.getEmployeecontract().getId());
                        warnings.add(warning);
                    } else {
                        throw new RuntimeException("query suboptimal");
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        }

        return warnings;
    }

    public List<Warning> createWarnings(Employeecontract employeecontract, Employeecontract loginEmployeeContract, MessageResources resources, Locale locale) {
        // warnings
        List<Warning> warnings = checkEmployeeorders(employeecontract, resources, locale);

        // timereport warning
        List<Timereport> timereports = timereportDAO.getTimereportsOutOfRangeForEmployeeContract(employeecontract);
        for (Timereport timereport : timereports) {
            Warning warning = new Warning();
            warning.setSort(resources.getMessage(locale, "main.info.warning.timereportnotinrange"));
            warning.setText(timereport.getTimeReportAsString());
            warnings.add(warning);
        }

        // timereport warning 2
        timereports = timereportDAO.getTimereportsOutOfRangeForEmployeeOrder(employeecontract);
        for (Timereport timereport : timereports) {
            Warning warning = new Warning();
            warning.setSort(resources.getMessage(locale, "main.info.warning.timereportnotinrangeforeo"));
            warning.setText(timereport.getTimeReportAsString() + " " + timereport.getEmployeeorder().getEmployeeOrderAsString());
            warnings.add(warning);
        }

        // timereport warning 3: no duration
        timereports = timereportDAO.getTimereportsWithoutDurationForEmployeeContractId(employeecontract.getId(), employeecontract.getReportReleaseDate());
        for (Timereport timereport : timereports) {
            Warning warning = new Warning();
            warning.setSort(resources.getMessage(locale, "main.info.warning.timereport.noduration"));
            warning.setText(timereport.getTimeReportAsString());
            if (loginEmployeeContract.equals(employeecontract)
                    || loginEmployeeContract.getEmployee().getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL)
                    || loginEmployeeContract.getEmployee().getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV)
                    || loginEmployeeContract.getEmployee().getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
                warning.setLink("/tb/do/EditDailyReport?trId=" + timereport.getId());
            }
            warnings.add(warning);
        }

        // statusreport due warning
        addWarnings(loginEmployeeContract, resources, locale, warnings);

        return warnings;
    }

    private void addWarnings(Employeecontract employeecontract, MessageResources resources, Locale locale, List<Warning> warnings) {
        // statusreport due warning
        List<Customerorder> customerOrders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeIdWithStatusReports(employeecontract.getEmployee().getId());
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);

        if (customerOrders != null && !customerOrders.isEmpty()) {

            java.util.Date now = new java.util.Date();

            for (Customerorder customerorder : customerOrders) {
                java.sql.Date maxUntilDate = statusReportDAO.getMaxUntilDateForCustomerOrderId(customerorder.getId());

                if (maxUntilDate == null) {
                    maxUntilDate = customerorder.getFromDate();
                }

                java.sql.Date checkDate = new java.sql.Date(maxUntilDate.getTime());

                GregorianCalendar calendar = new GregorianCalendar();
                calendar.setTime(checkDate);
                calendar.add(Calendar.MONTH, 12 / customerorder.getStatusreport());
                checkDate.setTime(calendar.getTimeInMillis());

                // final report due warning
                List<Statusreport> finalReports = statusReportDAO.getReleasedFinalStatusReportsByCustomerOrderId(customerorder.getId());
                if (customerorder.getStatusreport() > 0
                        && customerorder.getUntilDate() != null
                        && !customerorder.getUntilDate().after(now)
                        && (finalReports == null || finalReports.isEmpty())) {
                    Warning warning = new Warning();
                    warning.setSort(resources.getMessage(locale, "main.info.warning.statusreport.finalreport"));
                    warning.setText(customerorder.getSign() + " " + customerorder.getShortdescription());
                    List<Statusreport> unreleasedReports = statusReportDAO.getUnreleasedFinalStatusReports(customerorder.getId(), employeecontract.getEmployee().getId(), maxUntilDate);
                    if (unreleasedReports != null && !unreleasedReports.isEmpty()) {
                        if (unreleasedReports.size() == 1) {
                            warning.setLink("/tb/do/EditStatusReport?srId=" + unreleasedReports.get(0).getId());
                        } else {
                            warning.setLink("/tb/do/ShowStatusReport?coId=" + customerorder.getId());
                        }
                    } else {
                        warning.setLink("/tb/do/CreateStatusReport?coId=" + customerorder.getId() + "&final=true");
                    }
                    warnings.add(warning);
                }

                // periodical report due warning
                if (!checkDate.after(now) && (customerorder.getUntilDate() == null || customerorder.getUntilDate().after(checkDate))) {
                    // show warning
                    Warning warning = new Warning();
                    warning.setSort(resources.getMessage(locale, "main.info.warning.statusreport.due"));
                    warning.setText(customerorder.getSign() + " " + customerorder.getShortdescription() + " (" + simpleDateFormat.format(checkDate) + ")");
                    List<Statusreport> unreleasedReports = statusReportDAO.getUnreleasedPeriodicalStatusReports(customerorder.getId(), employeecontract.getEmployee().getId(), maxUntilDate);
                    if (unreleasedReports != null && !unreleasedReports.isEmpty()) {
                        if (unreleasedReports.size() == 1) {
                            warning.setLink("/tb/do/EditStatusReport?srId=" + unreleasedReports.get(0).getId());
                        } else {
                            warning.setLink("/tb/do/ShowStatusReport?coId=" + customerorder.getId());
                        }
                        warnings.add(warning);
                    } else {
                        if (finalReports.isEmpty()) {
                            warning.setLink("/tb/do/CreateStatusReport?coId=" + customerorder.getId() + "&final=false");
                            warnings.add(warning);
                        }
                    }
                }
            }
        }

        // statusreport acceptance warning
        List<Statusreport> reportsToBeAccepted = statusReportDAO.getReleasedStatusReportsByRecipientId(employeecontract.getEmployee().getId());
        if (reportsToBeAccepted != null && !reportsToBeAccepted.isEmpty()) {
            for (Statusreport statusreport : reportsToBeAccepted) {
                Warning warning = new Warning();
                warning.setSort(resources.getMessage(locale, "main.info.warning.statusreport.acceptance"));
                warning.setText(statusreport.getCustomerorder().getSign() + " "
                        + statusreport.getCustomerorder().getShortdescription()
                        + " (ID:" + statusreport.getId() + " "
                        + resources.getMessage(locale, "statusreport.from.text")
                        + ":" + simpleDateFormat.format(statusreport.getFromdate()) + " "
                        + resources.getMessage(locale, "statusreport.until.text")
                        + ":" + simpleDateFormat.format(statusreport.getUntildate()) + " "
                        + resources.getMessage(locale, "statusreport.from.text")
                        + ":" + statusreport.getSender().getName() + " "
                        + resources.getMessage(locale, "statusreport.to.text")
                        + ":" + statusreport.getRecipient().getName() + ")");
                warning.setLink("/tb/do/EditStatusReport?srId=" + statusreport.getId());
                warnings.add(warning);
            }
        }
    }

    public void handleOvertime(Employeecontract employeecontract, HttpSession session) {
        double overtimeStatic = employeecontract.getOvertimeStatic();
        int otStaticMinutes = (int) (overtimeStatic * 60);

        int overtime;
        if (employeecontract.getUseOvertimeOld() != null && !employeecontract.getUseOvertimeOld()) {
            //use new overtime computation with static + dynamic overtime
            //need the Date from the day after reportAcceptanceDate, so the latter is not used twice in overtime computation:
            Date dynamicDate;
            if (employeecontract.getReportAcceptanceDate() == null || employeecontract.getReportAcceptanceDate().equals(employeecontract.getValidFrom())) {
                dynamicDate = employeecontract.getValidFrom();
            } else {
                dynamicDate = DateUtils.addDays(employeecontract.getReportAcceptanceDate(), 1);
            }
            int overtimeDynamic = timereportHelper.calculateOvertime(dynamicDate, new Date(), employeecontract, true);
            overtime = otStaticMinutes + overtimeDynamic;
            // if after SALAT-Release 1.83, no Release was accepted yet, use old overtime computation
        } else {
            overtime = timereportHelper.calculateOvertimeTotal(employeecontract);
        }

        boolean overtimeIsNegative = overtime < 0;

        session.setAttribute("overtimeIsNegative", overtimeIsNegative);

        String overtimeString = timeFormatMinutes(overtime);
        session.setAttribute("overtime", overtimeString);

        //overtime this month
        Date start = java.sql.Date.valueOf(LocalDate.now().withDayOfMonth(1));
        Date currentDate = java.sql.Date.valueOf(LocalDate.now());

        java.sql.Date validFrom = employeecontract.getValidFrom();
        if (validFrom.after(start) && !validFrom.after(currentDate)) {
            start = validFrom;
        }
        java.sql.Date validUntil = employeecontract.getValidUntil();
        if (validUntil != null && validUntil.before(currentDate) && !validUntil.before(start)) {
            currentDate = validUntil;
        }
        int monthlyOvertime = 0;
        if (!(validUntil != null && validUntil.before(start) || validFrom.after(currentDate))) {
            monthlyOvertime = timereportHelper.calculateOvertime(start, currentDate, employeecontract, false);
        }
        boolean monthlyOvertimeIsNegative = monthlyOvertime < 0;
        session.setAttribute("monthlyOvertimeIsNegative", monthlyOvertimeIsNegative);
        String monthlyOvertimeString = timeFormatMinutes(monthlyOvertime);
        session.setAttribute("monthlyOvertime", monthlyOvertimeString);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM");
        session.setAttribute("overtimeMonth", format.format(start));

        //vacation v2 extracted to VacationViewer:
        VacationViewer vw = new VacationViewer(employeecontract);
        vw.computeVacations(session, employeecontract, employeeorderDAO, timereportDAO);
    }


    public void setEmployeeAttributes(HttpServletRequest request, Employee loginEmployee) {
        request.setAttribute("loginEmployee", loginEmployee);
        String loginEmployeeFullName = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
        request.setAttribute("loginEmployeeFullName", loginEmployeeFullName);
        request.setAttribute("report", "W");

        request.setAttribute("employeeId", loginEmployee.getId());
        request.setAttribute("currentEmployeeId", loginEmployee.getId());

        request.getSession().setAttribute("employeeAuthorized", employeeHasAuthorization(loginEmployee));

    }

    private boolean employeeHasAuthorization(Employee loginEmployee) {
        return loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_BL) ||
                loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_PV) ||
                loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM);
    }

    private void setEmployeeIsInternalAttribute(HttpServletRequest request) {
        String clientIP = request.getRemoteHost();
        boolean isInternal = clientIP.startsWith("10.") ||
                clientIP.startsWith("192.168.") ||
                clientIP.startsWith("172.16.") ||
                clientIP.startsWith("127.0.0.");
        request.getSession().setAttribute("clientIntern", isInternal);
    }

    /**
     * @param request
     * @param loginEmployee
     * @return an error-code
     */
    public String handle(HttpServletRequest request, Employee loginEmployee) {

        Date date = new Date();
        Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), date);
        if (employeecontract == null && !loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
            return "form.login.error.invalidcontract";
        }
        setEmployeeAttributes(request, loginEmployee);

        // check if user is internal or extern
        setEmployeeIsInternalAttribute(request);

        // check if public holidays are available
        publicholidayDAO.checkPublicHolidaysForCurrentYear();

        try {
            // check if employee has an employee contract and is has employee orders for all standard suborders
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
            String dateString = simpleDateFormat.format(date);
            date = simpleDateFormat.parse(dateString);
            if (employeecontract != null) {
                request.getSession().setAttribute("employeecontractId", employeecontract.getId());
                request.getSession().setAttribute("employeeHasValidContract", true);
                handleEmployeeWithValidContract(request, loginEmployee, date, employeecontract, dateString);
            } else {
                request.getSession().setAttribute("employeeHasValidContract", false);
            }
        } catch (ParseException e) {
            log.error("Error parsing date");
        }

        // create collection of employeecontracts
        List<Employeecontract> employeecontracts = employeecontractDAO.getVisibleEmployeeContractsForEmployee(loginEmployee);
        request.getSession().setAttribute("employeecontracts", employeecontracts);

        return null;
    }

    private void handleEmployeeWithValidContract(HttpServletRequest request, Employee loginEmployee, Date date,
                                                 Employeecontract employeecontract, String dateString) {
        // auto generate employee orders
        if (!loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM) &&
                Boolean.FALSE.equals(employeecontract.getFreelancer())) {
            generateEmployeeOrders(date, employeecontract, dateString);
        }

        if (employeecontract.getReportAcceptanceDate() == null) {
            java.sql.Date validFromDate = employeecontract.getValidFrom();
            employeecontract.setReportAcceptanceDate(validFromDate);
            // create tmp employee
            Employee tmp = new Employee();
            tmp.setSign(SYSTEM_SIGN);
            employeecontractDAO.save(employeecontract, tmp);
        }
        if (employeecontract.getReportReleaseDate() == null) {
            java.sql.Date validFromDate = employeecontract.getValidFrom();
            employeecontract.setReportReleaseDate(validFromDate);
            // create tmp employee
            Employee tmp = new Employee();
            tmp.setSign(SYSTEM_SIGN);
            employeecontractDAO.save(employeecontract, tmp);
        }
        // set used employee contract of login employee
        request.getSession().setAttribute("loginEmployeeContract", employeecontract);
        request.getSession().setAttribute("loginEmployeeContractId", employeecontract.getId());
        request.getSession().setAttribute("currentEmployeeContract", employeecontract);

        // get info about vacation, overtime and report status
        request.getSession().setAttribute("releaseWarning", employeecontract.getReleaseWarning());
        request.getSession().setAttribute("acceptanceWarning", employeecontract.getAcceptanceWarning());

        String releaseDate = employeecontract.getReportReleaseDateString();
        String acceptanceDate = employeecontract.getReportAcceptanceDateString();

        request.getSession().setAttribute("releasedUntil", releaseDate);
        request.getSession().setAttribute("acceptedUntil", acceptanceDate);

        handleOvertime(employeecontract, request.getSession());

        // get warnings
        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        List<Warning> warnings = createWarnings(employeecontract, loginEmployeeContract, getResources(
                request), getLocale(request));

        if (!warnings.isEmpty()) {
            request.getSession().setAttribute("warnings", warnings);
            request.getSession().setAttribute("warningsPresent", true);
        } else {
            request.getSession().setAttribute("warningsPresent", false);
        }
    }

    protected MessageResources getResources(HttpServletRequest request) {
        return (MessageResources)request.getAttribute("org.apache.struts.action.MESSAGE");
    }

    protected Locale getLocale(HttpServletRequest request) {
        return RequestUtils.getUserLocale(request, (String)null);
    }

    private void generateEmployeeOrders(Date date, Employeecontract employeecontract, String dateString2) {
        List<Suborder> standardSuborders = suborderDAO.getStandardSuborders();
        if (standardSuborders != null && !standardSuborders.isEmpty()) {
            // test if employeeorder exists
            for (Suborder suborder : standardSuborders) {
                List<Employeeorder> employeeorders = employeeorderDAO
                        .getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate3(
                                employeecontract.getId(), suborder
                                        .getId(), date);
                if (employeeorders == null || employeeorders.isEmpty()) {

                    // do not create an employeeorder for past years "URLAUB" !
                    if (suborder.getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
                            && !dateString2.startsWith(suborder.getSign())) {
                        continue;
                    }

                    // find latest untilDate of all employeeorders for this suborder
                    List<Employeeorder> invalidEmployeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(
                            employeecontract.getId(), suborder.getId());
                    Date dateUntil = null;
                    Date dateFrom = null;
                    for (Employeeorder eo : invalidEmployeeorders) {

                        // employeeorder starts in the future
                        if (eo.getFromDate() != null && eo.getFromDate().after(date)
                                && (dateUntil == null || dateUntil.after(eo.getFromDate()))) {

                            dateUntil = eo.getFromDate();
                            continue;
                        }

                        // employeeorder ends in the past
                        if (eo.getUntilDate() != null && eo.getUntilDate().before(date)
                                && (dateFrom == null || dateFrom.before(eo.getUntilDate()))) {

                            dateFrom = eo.getUntilDate();
                        }
                    }

                    // calculate time period
                    Date ecFromDate = employeecontract.getValidFrom();
                    Date ecUntilDate = employeecontract.getValidUntil();
                    Date soFromDate = suborder.getFromDate();
                    Date soUntilDate = suborder.getUntilDate();
                    Date fromDate = ecFromDate.before(soFromDate) ? soFromDate : ecFromDate;

                    // fromDate should not be before the ending of the most recent contract
                    if (dateFrom != null && dateFrom.after(fromDate)) {
                        fromDate = dateFrom;
                    }
                    Date untilDate = null;

                    if (ecUntilDate == null && soUntilDate == null) {
                        //untildate remains null
                    } else if (ecUntilDate == null) {
                        untilDate = soUntilDate;
                    } else if (soUntilDate == null) {
                        untilDate = ecUntilDate;
                    } else if (ecUntilDate.before(soUntilDate)) {
                        untilDate = ecUntilDate;
                    } else {
                        untilDate = soUntilDate;
                    }

                    Employeeorder employeeorder = new Employeeorder();

                    java.sql.Date sqlFromDate = new java.sql.Date(fromDate.getTime());
                    employeeorder.setFromDate(sqlFromDate);

                    // untilDate should not overreach a future employee contract
                    if (untilDate == null) {
                        untilDate = dateUntil;
                    } else {
                        if (dateUntil != null && dateUntil.before(untilDate)) {
                            untilDate = dateUntil;
                        }
                    }

                    if (untilDate != null) {
                        java.sql.Date sqlUntilDate = new java.sql.Date(untilDate.getTime());
                        employeeorder.setUntilDate(sqlUntilDate);
                    }
                    if (suborder.getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
                            && !suborder.getSign().equalsIgnoreCase(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
                        employeeorder.setDebithours(employeecontract
                                .getDailyWorkingTime()
                                * employeecontract
                                .getVacationEntitlement());
                        employeeorder.setDebithoursunit(GlobalConstants.DEBITHOURS_UNIT_TOTALTIME);
                    } else {
                        // not decided yet
                    }
                    employeeorder.setEmployeecontract(employeecontract);
                    employeeorder.setSign(" ");
                    employeeorder.setSuborder(suborder);

                    // create tmp employee
                    Employee tmp = new Employee();
                    tmp.setSign(SYSTEM_SIGN);

                    if (untilDate == null || !fromDate.after(untilDate)) {
                        employeeorderDAO.save(employeeorder, tmp);
                    }

                }
            }
        }
    }

}
