package org.tb.dailyreport.controller;

import static org.tb.common.util.DateUtils.addMonths;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.today;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.Authorized;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.service.ReleaseService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@Controller
@RequestMapping("/release")
@RequiredArgsConstructor
@Authorized
public class ReleaseController {

    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final ReleaseService releaseService;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;

    @GetMapping
    public String show(@RequestParam(required = false) Long employeeContractId, Model model) {

        var effectiveContractId = effectiveContractId(employeeContractId);
        var contract = employeecontractService.getEmployeecontractById(effectiveContractId);
        var employee = contract.getEmployee();

        model.addAttribute("employee", employee);
        model.addAttribute("employeeContract", contract);
        model.addAttribute("selfReleaseDateStr", defaultReleaseDateStr(contract));
        model.addAttribute("releasedUntil", format(contract.getReportReleaseDate()));
        model.addAttribute("acceptedUntil", format(contract.getReportAcceptanceDate()));
        model.addAttribute("section", "dailyreport");
        model.addAttribute("subSection", "release");
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.release.text"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.timereports.text"));
        return "dailyreport/release";
    }

    @PostMapping
    public String release(@RequestParam(required = false) Long employeeContractId,
                          @RequestParam(required = false) String selfReleaseDate,
                          RedirectAttributes redirectAttributes) {
        var effectiveContractId = effectiveContractId(employeeContractId);
        var contract = employeecontractService.getEmployeecontractById(effectiveContractId);
        if (contract == null) {
            return "redirect:/release";
        }
        try {
            releaseService.releaseTimereports(contract.getId(), parseEndOfMonth(selfReleaseDate));
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.release.releasetimeperiod.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastErrors", allMessages(ex));
        }
        return "redirect:/release";
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

    private String defaultReleaseDateStr(Employeecontract contract) {
        if (contract == null) return "";
        LocalDate rd = contract.getReportReleaseDate();
        LocalDate defaultDate = rd == null ? contract.getValidFrom() : addMonths(rd, 1);
        return YearMonth.from(defaultDate).toString();
    }

    private List<String> allMessages(ErrorCodeException ex) {
        var msgs = errorCodeViewHelper.toViewMessages(ex);
        if (msgs.isEmpty()) return List.of("Error");
        return msgs.stream().map(m -> m.resolved()).toList();
    }

    static LocalDate parseEndOfMonth(String s) {
        if (s == null || s.isBlank()) return today();
        return YearMonth.parse(s).atEndOfMonth();
    }
}
