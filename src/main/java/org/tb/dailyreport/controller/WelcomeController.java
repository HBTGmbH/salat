package org.tb.dailyreport.controller;

import static java.lang.Boolean.TRUE;
import static org.tb.common.util.DateUtils.today;
import static org.tb.dailyreport.viewhelper.OvertimeViewHelper.calculateAndSetOvertime;
import static org.tb.dailyreport.viewhelper.VacationViewHelper.calculateAndSetVacations;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.tb.auth.service.AuthService;
import org.tb.dailyreport.service.OvertimeService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.service.EmployeeorderService;

@Controller
@RequestMapping("/welcome")
@RequiredArgsConstructor
public class WelcomeController {

    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final AuthService authService;
    private final OvertimeService overtimeService;
    private final EmployeeorderService employeeorderService;
    private final TimereportService timereportService;
    private final MessageSourceAccessor messageSourceAccessor;

    @GetMapping
    public String welcome(HttpSession session, Model model) {
        var employeecontract = currentContract(session);
        session.setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
        session.setAttribute("currentEmployeeContract", employeecontract);

        var loginEmployees = employeeService.getLoginEmployees();
        session.setAttribute("loginEmployees", loginEmployees);

        var employeecontracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());
        session.setAttribute("employeecontracts", employeecontracts);

        calculateAndSetOvertime(session, employeecontract, overtimeService);
        calculateAndSetVacations(session, employeecontract, employeeorderService, timereportService);

        var warnings = timereportService.createTimeReportWarnings(employeecontract.getId(), messageSourceAccessor);

        session.setAttribute("releaseWarning", employeecontract.getReleaseWarning());
        session.setAttribute("acceptanceWarning", employeecontract.getAcceptanceWarning());
        session.setAttribute("releasedUntil", employeecontract.getReportReleaseDateString());
        session.setAttribute("acceptedUntil", employeecontract.getReportAcceptanceDateString());
        session.setAttribute("warnings", warnings);
        session.setAttribute("warningsPresent", !warnings.isEmpty());

        boolean displayEmployeeInfo = !TRUE.equals(employeecontract.getFreelancer());

        model.addAttribute("pageTitle", messageSourceAccessor.getMessage("main.general.mainmenu.overview.text"));
        model.addAttribute("employeecontracts", employeecontracts);
        model.addAttribute("loginEmployees", loginEmployees);
        model.addAttribute("currentEmployeeContractId", employeecontract.getId());
        model.addAttribute("currentLoginEmployeeId", employeecontract.getEmployee().getId());
        model.addAttribute("displayEmployeeInfo", displayEmployeeInfo);
        model.addAttribute("warnings", warnings);
        model.addAttribute("releasedUntil", employeecontract.getReportReleaseDateString());
        model.addAttribute("releaseColorClass", employeecontract.getReleaseWarning() ? "danger" : "success");
        model.addAttribute("acceptedUntil", employeecontract.getReportAcceptanceDateString());
        model.addAttribute("acceptanceColorClass", acceptanceColorClass(employeecontract.getReportAcceptanceDate()));
        model.addAttribute("overtime", session.getAttribute("overtime"));
        model.addAttribute("overtimeIsNegative", session.getAttribute("overtimeIsNegative"));
        model.addAttribute("overtimeColorClass", overtimeColorClass(employeecontract.getId()));
        model.addAttribute("monthlyOvertime", session.getAttribute("monthlyOvertime"));
        model.addAttribute("monthlyOvertimeIsNegative", session.getAttribute("monthlyOvertimeIsNegative"));
        model.addAttribute("overtimeMonth", session.getAttribute("overtimeMonth"));
        model.addAttribute("vacations", session.getAttribute("vacations"));

        return "dailyreport/welcome";
    }

    @PostMapping(params = "task=refresh")
    public String refresh(@RequestParam Long employeeContractId, HttpSession session) {
        var employeecontract = employeecontractService.getEmployeecontractById(employeeContractId);
        session.setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
        session.setAttribute("currentEmployeeContract", employeecontract);
        return "redirect:/welcome";
    }

    @PostMapping(params = "task=switch-login")
    public String switchLogin(@RequestParam Long loginEmployeeId, HttpSession session) {
        var switchedToEmployee = employeeService.getEmployeeById(loginEmployeeId);
        authService.switchLogin(switchedToEmployee.getLoginname());
        return "redirect:/welcome";
    }

    private Employeecontract currentContract(HttpSession session) {
        var contract = (Employeecontract) session.getAttribute("currentEmployeeContract");
        if (contract == null) {
            contract = (Employeecontract) session.getAttribute("loginEmployeeContract");
        }
        return contract;
    }

    /** Returns "success", "warning", or "danger" based on how far the acceptance date lags behind. */
    private String acceptanceColorClass(LocalDate acceptanceDate) {
        if (acceptanceDate == null) {
            return "success";
        }
        LocalDate firstOfCurrentMonth = today().withDayOfMonth(1);
        LocalDate firstOfPreviousMonth = firstOfCurrentMonth.minusMonths(1);
        if (acceptanceDate.isBefore(firstOfPreviousMonth)) {
            return "danger";   // 2+ months behind
        } else if (acceptanceDate.isBefore(firstOfCurrentMonth)) {
            return "warning";  // exactly 1 month behind
        }
        return "success";
    }

    /** Returns "success", "warning", or "danger" based on total overtime thresholds (in hours). */
    private String overtimeColorClass(long employeecontractId) {
        return overtimeService.calculateOvertime(employeecontractId, true)
            .map(status -> {
                long hours = status.getTotal().getDuration().toHours();
                long signedHours = status.getTotal().isNegative() ? -hours : hours;
                if (signedHours > 80 || signedHours < -40) return "danger";
                if (signedHours > 40 || signedHours < -20) return "warning";
                return "success";
            })
            .orElse("success");
    }

}
