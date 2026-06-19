package org.tb.dailyreport.controller;

import static org.tb.common.util.DateUtils.today;

import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.Authorized;
import org.tb.common.web.UiState;
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
    private final UiState uiState;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String show(Model model) {
        long ecId = effectiveContractId();
        var contracts = employeecontractService
            .getViewableEmployeeContractsForAuthorizedUserValidAt(today());
        OvertimeReport report = ecId > 0
            ? overtimeService.createDetailedReportForEmployee(ecId)
            : null;

        model.addAttribute("employeecontracts", contracts);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("overtimeReport", report);
        model.addAttribute("section",    "dailyreport");
        model.addAttribute("subSection", "overtime");
        model.addAttribute("pageTitle",  messages.getMessage("main.general.mainmenu.overtime.text"));
        return "dailyreport/overtime";
    }

    @PostMapping("/correct")
    @PreAuthorize("hasRole('MANAGER')")
    public String correctOvertime(RedirectAttributes redirectAttributes) {
        long ecId = effectiveContractId();
        if (ecId > 0) {
            overtimeService.updateOvertimeStatic(ecId);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.overtime.employeecontract.correct.label"));
        }
        return "redirect:/dailyreport/overtime";
    }

    private long effectiveContractId() {
        Long fromCookie = uiState.getSelectedContractId();
        if (fromCookie != null && fromCookie > 0) return fromCookie;
        var loginEmployee = employeeService.getLoginEmployee();
        return employeecontractService.getCurrentContract(loginEmployee.getId())
            .map(Employeecontract::getId)
            .orElse(-1L);
    }

}
