package org.tb.dailyreport.controller;

import static org.tb.common.util.DateUtils.addMonths;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.today;
import static org.tb.dailyreport.controller.ReleaseController.parseEndOfMonth;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
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
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.service.ReleaseService;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@Controller
@RequestMapping("/acceptance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PEOPLE_LEAD')")
public class AcceptanceController {

    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final ReleaseService releaseService;
    private final AuthorizedUser authorizedUser;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;

    @GetMapping
    public String show(@RequestParam(required = false) Long contractId,
                       @RequestParam(required = false) Long supervisorId,
                       Model model) {
        var loginEmployee = employeeService.getLoginEmployee();

        if (authorizedUser.isManager()) {
            var allViewable = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());
            List<Employee> supervisors = allViewable.stream()
                .flatMap(ec -> ec.getSupervisors().stream())
                .distinct()
                .sorted(Comparator.comparing(Employee::getName))
                .toList();
            model.addAttribute("supervisors", supervisors);
            if (supervisorId == null && supervisors.stream().anyMatch(s -> s.getId().equals(loginEmployee.getId()))) {
                supervisorId = loginEmployee.getId();
            }
        }

        List<Employeecontract> employeeContracts = loadContracts(loginEmployee, supervisorId);
        employeeContracts = employeeContracts.stream()
            .sorted(Comparator.comparing(ec -> ec.getEmployee().getName()))
            .toList();

        Employeecontract selected = null;
        if (contractId != null) {
            selected = employeecontractService.getEmployeecontractById(contractId);
        }
        if (selected == null && !employeeContracts.isEmpty()) {
            selected = employeeContracts.getFirst();
        }

        model.addAttribute("employeeContracts", employeeContracts);
        model.addAttribute("selectedContract", selected);
        model.addAttribute("supervisorId", supervisorId);
        model.addAttribute("releasedUntil", selected != null ? format(selected.getReportReleaseDate()) : "");
        model.addAttribute("acceptedUntil", selected != null ? format(selected.getReportAcceptanceDate()) : "");
        model.addAttribute("releaseDateStr", defaultReleaseDateStr(selected));
        model.addAttribute("acceptanceDateStr", defaultAcceptanceDateStr(selected));
        model.addAttribute("reopenDateStr", defaultReleaseDateStr(selected));
        model.addAttribute("section", "dailyreport");
        model.addAttribute("subSection", "acceptance");
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.acceptance.text"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.timereports.text"));
        return "dailyreport/acceptance";
    }

    @PostMapping("/release")
    public String release(@RequestParam Long contractId,
                          @RequestParam(required = false) String releaseDate,
                          RedirectAttributes redirectAttributes) {
        try {
            releaseService.releaseTimereports(contractId, parseEndOfMonth(releaseDate));
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.release.releasetimeperiod.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastErrors", allMessages(ex));
        }
        return "redirect:/acceptance?contractId=" + contractId;
    }

    @PostMapping("/accept")
    public String accept(@RequestParam Long contractId,
                         @RequestParam(required = false) String acceptanceDate,
                         RedirectAttributes redirectAttributes) {
        try {
            releaseService.acceptTimereports(contractId, parseEndOfMonth(acceptanceDate));
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.release.accepttimeperiod.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastErrors", allMessages(ex));
        }
        return "redirect:/acceptance?contractId=" + contractId;
    }

    @PostMapping("/reopen")
    public String reopen(@RequestParam Long contractId,
                         @RequestParam(required = false) String reopenDate,
                         RedirectAttributes redirectAttributes) {
        try {
            releaseService.reopenTimereports(contractId, parseStartOfMonth(reopenDate));
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.release.reopentimeperiod.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastErrors", allMessages(ex));
        }
        return "redirect:/acceptance?contractId=" + contractId;
    }

    @PostMapping("/release-mail")
    public String sendReleaseMail(@RequestParam Long employeeId,
                                  @RequestParam(required = false) Long contractId,
                                  RedirectAttributes redirectAttributes) {
        try {
            releaseService.sendReleaseReminderMail(employeeId);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.release.actioninfo.mailsent.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastErrors", allMessages(ex));
        }
        return contractId != null ? "redirect:/acceptance?contractId=" + contractId : "redirect:/acceptance";
    }

    @PostMapping("/acceptance-mail")
    public String sendAcceptanceMail(@RequestParam Long contractId,
                                     RedirectAttributes redirectAttributes) {
        try {
            releaseService.sendAcceptanceReminderMail(contractId);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.release.actioninfo.mailsent.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastErrors", allMessages(ex));
        }
        return "redirect:/acceptance?contractId=" + contractId;
    }

    private List<Employeecontract> loadContracts(Employee loginEmployee, Long supervisorId) {
        if (authorizedUser.isManager()) {
            if (supervisorId == null || supervisorId == -1L) {
                return employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());
            }
            return employeecontractService.getTeamContracts(supervisorId);
        }
        return employeecontractService.getTeamContracts(loginEmployee.getId());
    }

    private String defaultReleaseDateStr(Employeecontract contract) {
        if (contract == null) return "";
        LocalDate rd = contract.getReportReleaseDate();
        LocalDate defaultDate = rd == null ? contract.getValidFrom() : addMonths(rd, 1);
        return YearMonth.from(defaultDate).toString();
    }

    private String defaultAcceptanceDateStr(Employeecontract contract) {
        if (contract == null) return "";
        LocalDate ad = contract.getReportAcceptanceDate();
        LocalDate defaultDate = ad == null ? contract.getValidFrom() : ad;
        return YearMonth.from(defaultDate).toString();
    }

    private List<String> allMessages(ErrorCodeException ex) {
        var msgs = errorCodeViewHelper.toViewMessages(ex);
        if (msgs.isEmpty()) return List.of("Error");
        return msgs.stream().map(m -> m.resolved()).toList();
    }

    private static LocalDate parseStartOfMonth(String s) {
        if (s == null || s.isBlank()) return today();
        return YearMonth.parse(s).atDay(1);
    }
}
