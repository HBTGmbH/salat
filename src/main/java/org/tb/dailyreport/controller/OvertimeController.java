package org.tb.dailyreport.controller;

import static org.tb.common.util.DateUtils.today;

import java.time.Duration;
import java.time.LocalDate;

import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.Authorized;
import org.tb.dailyreport.domain.OvertimeReport;
import org.tb.dailyreport.service.OvertimeService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.employee.service.EmployeeService;

@Controller
@RequestMapping("/dailyreport/overtime")
@RequiredArgsConstructor
@Authorized
public class OvertimeController {

    private final OvertimeService overtimeService;
    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final MessageSourceAccessor messages;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String show(@RequestParam(required = false) Long employeeContractId, Model model) {
        long ecId = effectiveContractId(employeeContractId);
        var contracts = employeecontractService
            .getViewableEmployeeContractsForAuthorizedUserValidAt(today());
        OvertimeReport report = ecId > 0
            ? overtimeService.createDetailedReportForEmployee(ecId)
            : null;

        Duration overtimeMismatch = null;
        LocalDate reportReleaseDate = null;
        LocalDate reportAcceptanceDate = null;
        if (report != null) {
            var storedOvertime = overtimeService.calculateOvertime(ecId, true);
            if (storedOvertime.isPresent()) {
                Duration detailed = report.getTotal().getDiffCumulative();
                Duration stored = storedOvertime.get().getTotal().getDuration();
                if (!detailed.equals(stored)) {
                    overtimeMismatch = detailed.minus(stored);
                }
            }
            var contract = contracts.stream()
                .filter(ec -> ec.getId() == ecId)
                .findFirst()
                .orElseGet(() -> employeecontractService.getEmployeecontractById(ecId));
            reportReleaseDate = contract.getReportReleaseDate();
            reportAcceptanceDate = contract.getReportAcceptanceDate();
        }

        model.addAttribute("employeecontracts", contracts);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("overtimeReport", report);
        model.addAttribute("overtimeMismatch", overtimeMismatch);
        model.addAttribute("reportReleaseDate", reportReleaseDate);
        model.addAttribute("reportAcceptanceDate", reportAcceptanceDate);
        model.addAttribute("section",    "dailyreport");
        model.addAttribute("subSection", "overtime");
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.timereports.text"));
        model.addAttribute("pageTitle",  messages.getMessage("main.general.mainmenu.overtime.text"));
        return "dailyreport/overtime";
    }

    @PostMapping("/correct")
    @PreAuthorize("hasRole('MANAGER')")
    public String correctOvertime(@RequestParam(required = false) Long employeeContractId, RedirectAttributes redirectAttributes) {
        long ecId = effectiveContractId(employeeContractId);
        if (ecId > 0) {
            overtimeService.updateOvertimeStatic(ecId);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.overtime.employeecontract.correct.label"));
        }
        return "redirect:/dailyreport/overtime";
    }

    private long effectiveContractId(Long employeeContractId) {
        if (employeeContractId != null && employeeContractId > 0) {
            return employeeContractId;
        }
        var loginEmployee = employeeService.getLoginEmployee();
        return employeecontractService.getCurrentContract(loginEmployee.getId())
            .map(Employeecontract::getId)
            .orElse(-1L);
    }

}
