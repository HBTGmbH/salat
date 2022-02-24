package org.tb.helper;

import static org.tb.util.DateUtils.addDays;
import static org.tb.util.DateUtils.format;
import static org.tb.util.DateUtils.getBeginOfMonth;
import static org.tb.util.DateUtils.today;
import static org.tb.util.TimeFormatUtils.timeFormatMinutes;
import static org.tb.util.UrlUtils.absoluteUrl;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.util.MessageResources;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Statusreport;
import org.tb.bdom.Timereport;
import org.tb.bdom.Warning;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.StatusReportDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.util.DateUtils;

@Component
@Slf4j
@RequiredArgsConstructor(onConstructor_ = { @Autowired})
public class AfterLogin {

    private final TimereportHelper timereportHelper;
    private final EmployeeorderDAO employeeorderDAO;
    private final TimereportDAO timereportDAO;
    private final StatusReportDAO statusReportDAO;
    private final CustomerorderDAO customerorderDAO;
    private final ServletContext servletContext;

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
                        warning.setLink(absoluteUrl("/do/ShowEmployeeorder?employeeContractId=" + employeeorder.getEmployeecontract().getId(), servletContext));
                        warnings.add(warning);
                    } else if (employeeorder.getEmployeeOrderContent() != null && !employeeorder.getEmployeeOrderContent().getCommitted_mgmt()
                            && employeeorder.getEmployeeOrderContent().getContactTechHbt().equals(employeecontract.getEmployee())) {
                        Warning warning = new Warning();
                        warning.setSort(resources.getMessage(locale, "employeeordercontent.thumbdown.text"));
                        warning.setText(employeeorder.getEmployeeOrderAsString());
                        warning.setLink(absoluteUrl("/do/ShowEmployeeorder?employeeContractId=" + employeeorder.getEmployeecontract().getId(), servletContext));
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

    public List<Warning> createWarnings(Employeecontract employeecontract, Employeecontract loginEmployeeContract,
        MessageResources resources, Locale locale) {
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
                warning.setLink(absoluteUrl("/do/EditDailyReport?trId=" + timereport.getId(), servletContext));
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
        if (customerOrders != null && !customerOrders.isEmpty()) {

            LocalDate now = DateUtils.today();

            for (Customerorder customerorder : customerOrders) {
                LocalDate maxUntilDate = statusReportDAO.getMaxUntilDateForCustomerOrderId(customerorder.getId());

                if (maxUntilDate == null) {
                    maxUntilDate = customerorder.getFromDate();
                }

                LocalDate checkDate = maxUntilDate;
                checkDate = DateUtils.addMonths(checkDate, 12 / customerorder.getStatusreport());

                // final report due warning
                List<Statusreport> finalReports = statusReportDAO.getReleasedFinalStatusReportsByCustomerOrderId(customerorder.getId());
                if (customerorder.getStatusreport() > 0
                        && customerorder.getUntilDate() != null
                        && !customerorder.getUntilDate().isAfter(now)
                        && (finalReports == null || finalReports.isEmpty())) {
                    Warning warning = new Warning();
                    warning.setSort(resources.getMessage(locale, "main.info.warning.statusreport.finalreport"));
                    warning.setText(customerorder.getSign() + " " + customerorder.getShortdescription());
                    List<Statusreport> unreleasedReports = statusReportDAO.getUnreleasedFinalStatusReports(customerorder.getId(), employeecontract.getEmployee().getId(), maxUntilDate);
                    if (unreleasedReports != null && !unreleasedReports.isEmpty()) {
                        if (unreleasedReports.size() == 1) {
                            warning.setLink(absoluteUrl("/do/EditStatusReport?srId=" + unreleasedReports.get(0).getId(), servletContext));
                        } else {
                            warning.setLink(absoluteUrl("/do/ShowStatusReport?coId=" + customerorder.getId(), servletContext));
                        }
                    } else {
                        warning.setLink(absoluteUrl("/do/CreateStatusReport?coId=" + customerorder.getId() + "&final=true", servletContext));
                    }
                    warnings.add(warning);
                }

                // periodical report due warning
                if (!checkDate.isAfter(now) && (customerorder.getUntilDate() == null || customerorder.getUntilDate().isAfter(checkDate))) {
                    // show warning
                    Warning warning = new Warning();
                    warning.setSort(resources.getMessage(locale, "main.info.warning.statusreport.due"));
                    warning.setText(customerorder.getSign() + " " + customerorder.getShortdescription() + " (" + format(checkDate) + ")");
                    List<Statusreport> unreleasedReports = statusReportDAO.getUnreleasedPeriodicalStatusReports(customerorder.getId(), employeecontract.getEmployee().getId(), maxUntilDate);
                    if (unreleasedReports != null && !unreleasedReports.isEmpty()) {
                        if (unreleasedReports.size() == 1) {
                            warning.setLink(absoluteUrl("/do/EditStatusReport?srId=" + unreleasedReports.get(0).getId(), servletContext));
                        } else {
                            warning.setLink(absoluteUrl("/do/ShowStatusReport?coId=" + customerorder.getId(), servletContext));
                        }
                        warnings.add(warning);
                    } else {
                        if (finalReports == null || finalReports.isEmpty()) {
                            warning.setLink(absoluteUrl("/do/CreateStatusReport?coId=" + customerorder.getId() + "&final=false", servletContext));
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
                        + ":" + format(statusreport.getFromdate()) + " "
                        + resources.getMessage(locale, "statusreport.until.text")
                        + ":" + format(statusreport.getUntildate()) + " "
                        + resources.getMessage(locale, "statusreport.from.text")
                        + ":" + statusreport.getSender().getName() + " "
                        + resources.getMessage(locale, "statusreport.to.text")
                        + ":" + statusreport.getRecipient().getName() + ")");
                warning.setLink(absoluteUrl("/do/EditStatusReport?srId=" + statusreport.getId(), servletContext));
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
            //need the LocalDate from the day after reportAcceptanceDate, so the latter is not used twice in overtime computation:
            LocalDate dynamicDate;
            if (employeecontract.getReportAcceptanceDate() == null || employeecontract.getReportAcceptanceDate().equals(employeecontract.getValidFrom())) {
                dynamicDate = employeecontract.getValidFrom();
            } else {
                dynamicDate = addDays(employeecontract.getReportAcceptanceDate(), 1);
            }
            int overtimeDynamic = timereportHelper.calculateOvertime(dynamicDate, today(), employeecontract, true);
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
        LocalDate start = getBeginOfMonth(today());
        LocalDate currentDate = today();

        LocalDate validFrom = employeecontract.getValidFrom();
        if (validFrom.isAfter(start) && !validFrom.isAfter(currentDate)) {
            start = validFrom;
        }
        LocalDate validUntil = employeecontract.getValidUntil();
        if (validUntil != null && validUntil.isBefore(currentDate) && !validUntil.isBefore(start)) {
            currentDate = validUntil;
        }
        int monthlyOvertime = 0;
        if (!(validUntil != null && validUntil.isBefore(start) || validFrom.isAfter(currentDate))) {
            monthlyOvertime = timereportHelper.calculateOvertime(start, currentDate, employeecontract, false);
        }
        boolean monthlyOvertimeIsNegative = monthlyOvertime < 0;
        session.setAttribute("monthlyOvertimeIsNegative", monthlyOvertimeIsNegative);
        String monthlyOvertimeString = timeFormatMinutes(monthlyOvertime);
        session.setAttribute("monthlyOvertime", monthlyOvertimeString);

        session.setAttribute("overtimeMonth", DateUtils.format(start, "yyyy-MM"));

        //vacation v2 extracted to VacationViewer:
        VacationViewer vw = new VacationViewer(employeecontract);
        vw.computeVacations(session, employeecontract, employeeorderDAO, timereportDAO);
    }
}
