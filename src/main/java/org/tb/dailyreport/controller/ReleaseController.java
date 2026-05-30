package org.tb.dailyreport.controller;

import static org.tb.common.util.DateUtils.addMonths;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.today;

import java.time.LocalDate;
import java.time.YearMonth;
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
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.service.ReleaseService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@Controller
@RequestMapping("/release")
@RequiredArgsConstructor
@PreAuthorize("not hasRole('RESTRICTED')")
public class ReleaseController {

    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final ReleaseService releaseService;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;

    @GetMapping
    public String show(Model model) {
        var loginEmployee = employeeService.getLoginEmployee();
        var contract = employeecontractService.getCurrentContract(loginEmployee.getId()).orElse(null);

        model.addAttribute("loginEmployee", loginEmployee);
        model.addAttribute("loginEmployeeContract", contract);
        model.addAttribute("selfReleaseDateStr", defaultReleaseDateStr(contract));
        model.addAttribute("releasedUntil", contract != null ? format(contract.getReportReleaseDate()) : "");
        model.addAttribute("acceptedUntil", contract != null ? format(contract.getReportAcceptanceDate()) : "");
        model.addAttribute("section", "release");
        model.addAttribute("subSection", "release");
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.release.title.text"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.release.title.text"));
        return "dailyreport/release";
    }

    @PostMapping
    public String release(@RequestParam(required = false) String selfReleaseDate,
                          RedirectAttributes redirectAttributes) {
        var loginEmployee = employeeService.getLoginEmployee();
        var contract = employeecontractService.getCurrentContract(loginEmployee.getId()).orElse(null);
        if (contract == null) {
            return "redirect:/release";
        }
        try {
            releaseService.releaseTimereports(contract.getId(), parseEndOfMonth(selfReleaseDate));
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.release.releasetimeperiod.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError", firstMessage(ex));
        }
        return "redirect:/release";
    }

    private String defaultReleaseDateStr(Employeecontract contract) {
        if (contract == null) return "";
        LocalDate rd = contract.getReportReleaseDate();
        LocalDate defaultDate = rd == null ? contract.getValidFrom() : addMonths(rd, 1);
        return YearMonth.from(defaultDate).toString();
    }

    private String firstMessage(ErrorCodeException ex) {
        var msgs = errorCodeViewHelper.toViewMessages(ex);
        return msgs.isEmpty() ? "Error" : msgs.getFirst().resolved();
    }

    static LocalDate parseEndOfMonth(String s) {
        if (s == null || s.isBlank()) return today();
        return YearMonth.parse(s).atEndOfMonth();
    }
}
