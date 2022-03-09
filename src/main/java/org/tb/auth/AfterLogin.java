package org.tb.auth;

import static org.tb.common.util.DateUtils.addDays;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.getBeginOfMonth;
import static org.tb.common.util.DateUtils.today;
import static org.tb.common.util.TimeFormatUtils.timeFormatMinutes;
import static org.tb.common.util.UrlUtils.absoluteUrl;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.util.MessageResources;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.Warning;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.Timereport;
import org.tb.dailyreport.TimereportDAO;
import org.tb.dailyreport.viewhelper.TimereportHelper;
import org.tb.dailyreport.viewhelper.VacationViewer;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.CustomerorderDAO;
import org.tb.order.EmployeeorderDAO;

@Component
@Slf4j
@RequiredArgsConstructor
public class AfterLogin {

    private final TimereportHelper timereportHelper;
    private final EmployeeorderDAO employeeorderDAO;
    private final TimereportDAO timereportDAO;
    private final CustomerorderDAO customerorderDAO;
    private final ServletContext servletContext;

    public List<Warning> createWarnings(Employeecontract employeecontract, Employeecontract loginEmployeeContract,
        MessageResources resources, Locale locale) {
        // warnings
        List<Warning> warnings = new ArrayList<>();

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

        return warnings;
    }

    public void handleOvertime(Employeecontract employeecontract, HttpSession session) {
        Duration overtimeStatic = employeecontract.getOvertimeStatic();
        long otStaticMinutes = overtimeStatic.toMinutes();

        //use new overtime computation with static + dynamic overtime
        //need the LocalDate from the day after reportAcceptanceDate, so the latter is not used twice in overtime computation:
        LocalDate dynamicDate;
        if (employeecontract.getReportAcceptanceDate() == null || employeecontract.getReportAcceptanceDate().equals(employeecontract.getValidFrom())) {
            dynamicDate = employeecontract.getValidFrom();
        } else {
            dynamicDate = addDays(employeecontract.getReportAcceptanceDate(), 1);
        }
        long overtimeDynamic = timereportHelper.calculateOvertime(dynamicDate, today(), employeecontract, true);
        long overtime = otStaticMinutes + overtimeDynamic;

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
        long monthlyOvertime = 0;
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
