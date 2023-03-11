package org.tb.auth;

import static org.tb.common.util.UrlUtils.absoluteUrl;

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
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.viewhelper.VacationViewer;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.OvertimeService;
import org.tb.order.persistence.EmployeeorderDAO;

@Component
@Slf4j
@RequiredArgsConstructor
public class AfterLogin {

    private final EmployeeorderDAO employeeorderDAO;
    private final TimereportDAO timereportDAO;
    private final OvertimeService overtimeService;
    private final ServletContext servletContext;

    public List<Warning> createWarnings(Employeecontract employeecontract, Employeecontract loginEmployeeContract,
        MessageResources resources, Locale locale) {
        // warnings
        List<Warning> warnings = new ArrayList<>();

        // timereport warning
        List<TimereportDTO> timereports = timereportDAO.getTimereportsOutOfRangeForEmployeeContract(employeecontract);
        for (TimereportDTO timereport : timereports) {
            Warning warning = new Warning();
            warning.setSort(resources.getMessage(locale, "main.info.warning.timereportnotinrange"));
            warning.setText(timereport.getTimeReportAsString());
            warnings.add(warning);
        }

        // timereport warning 2
        timereports = timereportDAO.getTimereportsOutOfRangeForEmployeeOrder(employeecontract);
        for (TimereportDTO timereport : timereports) {
            Warning warning = new Warning();
            warning.setSort(resources.getMessage(locale, "main.info.warning.timereportnotinrangeforeo"));
            warning.setText(timereport.getTimeReportAsString() + " " + timereport.getEmployeeOrderAsString());
            warnings.add(warning);
        }

        // timereport warning 3: no duration
        timereports = timereportDAO.getTimereportsWithoutDurationForEmployeeContractId(employeecontract.getId(), employeecontract.getReportReleaseDate());
        for (TimereportDTO timereport : timereports) {
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
        var overtimeStatus = overtimeService.calculateOvertime(employeecontract.getId(), true);
        overtimeStatus.ifPresentOrElse(
            status -> {
                var overtimeIsNegative = status.getTotal().isNegative();
                session.setAttribute("overtimeIsNegative", overtimeIsNegative);

                String overtimeString = DurationUtils.format(status.getTotal().getDuration());
                session.setAttribute("overtime", overtimeString);

                if(status.getCurrentMonth() != null) {
                    var monthlyOvertimeIsNegative = status.getCurrentMonth().isNegative();
                    session.setAttribute("monthlyOvertimeIsNegative", monthlyOvertimeIsNegative);

                    String monthlyOvertimeString = DurationUtils.format(status.getCurrentMonth().getDuration());
                    session.setAttribute("monthlyOvertime", monthlyOvertimeString);

                    session.setAttribute("overtimeMonth", DateUtils.format(status.getCurrentMonth().getBegin(), "yyyy-MM"));
                } else {
                    session.setAttribute("monthlyOvertimeIsNegative", false);
                    session.setAttribute("monthlyOvertime", "");
                    session.setAttribute("overtimeMonth", "");
                }
            },
            () -> {
                session.setAttribute("overtimeIsNegative", false);
                session.setAttribute("overtime", "");
                session.setAttribute("monthlyOvertimeIsNegative", false);
                session.setAttribute("monthlyOvertime", "");
                session.setAttribute("overtimeMonth", "");
            }
        );

        //vacation v2 extracted to VacationViewer:
        VacationViewer vw = new VacationViewer(employeecontract);
        vw.computeVacations(session, employeecontract, employeeorderDAO, timereportDAO);
    }
}
