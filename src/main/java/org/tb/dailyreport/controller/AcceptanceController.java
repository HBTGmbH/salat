package org.tb.dailyreport.controller;

import static org.tb.common.util.DateUtils.addMonths;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.min;
import static org.tb.common.util.DateUtils.today;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.service.ReleaseService;
import org.tb.dailyreport.viewhelper.ReviewLinks;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@Controller
@RequestMapping("/acceptance")
@RequiredArgsConstructor
@Authorized(requiresPeopleLead = true)
public class AcceptanceController {

    private static final String RELEASE_REVIEW_PATH = "/acceptance/release/review";
    private static final String ACCEPT_REVIEW_PATH = "/acceptance/accept/review";

    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final ReleaseService releaseService;
    private final AuthorizedUser authorizedUser;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;

    @GetMapping
    public String show(@RequestParam(required = false) Long fAcceptanceEmployeeContractId,
                       @RequestParam(required = false) Long fAcceptanceSupervisorId,
                       Model model) {
        var loginEmployee = employeeService.getLoginEmployee();

        if (authorizedUser.isManager()) {
            var allViewable = employeecontractService.getVisibleEmployeeContractsForAuthorizedUser();
            List<Employee> supervisors = allViewable.stream()
                .flatMap(ec -> ec.getSupervisors().stream())
                .distinct()
                .sorted(Comparator.comparing(Employee::getName))
                .toList();
            model.addAttribute("supervisors", supervisors);
            if (fAcceptanceSupervisorId == null && supervisors.stream().anyMatch(s -> s.getId().equals(loginEmployee.getId()))) {
                fAcceptanceSupervisorId = loginEmployee.getId();
            }
        }

        List<Employeecontract> employeeContracts = loadContracts(loginEmployee, fAcceptanceSupervisorId);
        employeeContracts = employeeContracts.stream()
            .sorted(Comparator.comparing(ec -> ec.getEmployee().getName()))
            .toList();

        Employeecontract selected = null;
        if (fAcceptanceEmployeeContractId != null) {
            selected = employeecontractService.getEmployeecontractById(fAcceptanceEmployeeContractId);
        }
        if (selected == null && !employeeContracts.isEmpty()) {
            selected = employeeContracts.getFirst();
        }

        model.addAttribute("employeeContracts", employeeContracts);
        model.addAttribute("selectedContract", selected);
        model.addAttribute("supervisorId", fAcceptanceSupervisorId);
        model.addAttribute("releasedUntil", selected != null ? format(selected.getReportReleaseDate()) : "");
        model.addAttribute("acceptedUntil", selected != null ? format(selected.getReportAcceptanceDate()) : "");
        model.addAttribute("releaseDateStr", defaultReleaseDateStr(selected));
        model.addAttribute("acceptanceDateStr", defaultAcceptanceDateStr(selected));
        model.addAttribute("acceptanceMaxMonthStr", acceptanceMaxMonthStr(selected));
        model.addAttribute("acceptAllowed", selected != null && releaseService.isAcceptAllowed(selected.getId()));
        model.addAttribute("reopenDateStr", defaultReleaseDateStr(selected));
        model.addAttribute("lastMonthStr", lastMonthStr(selected));
        model.addAttribute("section", "backoffice");
        model.addAttribute("subSection", "acceptance");
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.acceptance.text"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.backoffice.text"));
        return "dailyreport/acceptance";
    }

    /**
     * Die Übersicht vor der Freigabe für die gewählte Person (#760) — dieselbe wie bei der eigenen
     * Freigabe. Anders als dort nennt die Adresse den Vertrag, denn hier ist nicht die gemerkte
     * Auswahl der Tagesansicht gemeint, sondern die der Abnahme. Ohne Vertrag oder Monat geht es
     * zurück zur Abnahme; wer für den Vertrag nicht freigeben darf, bekommt 403 (Service).
     */
    @GetMapping("/release/review")
    public String releaseReview(@RequestParam(required = false) Long contractId,
                                @RequestParam(required = false) String until,
                                @RequestParam(required = false) String view,
                                Model model) {
        var month = ReviewPage.month(until);
        if (contractId == null || month.isEmpty()
            || employeecontractService.getEmployeecontractById(contractId) == null) {
            return "redirect:/acceptance";
        }
        var effectiveView = ReviewLinks.viewOf(view);
        var review = releaseService.reviewRelease(contractId, month.get().atEndOfMonth());
        var links = ReviewLinks.of(RELEASE_REVIEW_PATH, contractId, month.get(), effectiveView,
            "/acceptance/release", "/acceptance");
        ReviewPage.addReview(model, review, links, effectiveView, errorCodeViewHelper);
        model.addAttribute("section", "backoffice");
        model.addAttribute("subSection", "acceptance");
        model.addAttribute("pageTitle", messages.getMessage("main.release.review.title.release.text"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.backoffice.text"));
        return ReviewPage.RELEASE_VIEW_NAME;
    }

    /**
     * Gibt genau den Zeitraum frei, den die Übersicht gezeigt hat (#760). Nach dem Freigeben geht es
     * ohne Filterparameter zurück: die Auswahl der Abnahme ist gemerkt, und Speichern ändert den
     * Filter nicht (ADR-0023). Scheitert es, geht es zurück in die Übersicht derselben Person.
     */
    @PostMapping("/release")
    public String release(@RequestParam long contractId,
                          @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate periodBegin,
                          @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate periodEnd,
                          @RequestParam(required = false) String view,
                          RedirectAttributes redirectAttributes) {
        try {
            releaseService.releaseTimereports(contractId, periodBegin, periodEnd);
            redirectAttributes.addFlashAttribute("toastSuccess",
                ReviewPage.releasedMessage(messages, periodBegin, periodEnd));
            return "redirect:/acceptance";
        } catch (BusinessRuleException | InvalidDataException ex) {
            redirectAttributes.addFlashAttribute("toastError", ReviewPage.failureMessage(ex, errorCodeViewHelper,
                messages, "main.release.review.notreleased.text"));
            return "redirect:" + ReviewLinks.of(RELEASE_REVIEW_PATH, contractId, YearMonth.from(periodEnd),
                ReviewLinks.viewOf(view), "/acceptance/release", "/acceptance").currentUrl();
        }
    }

    /**
     * Die Übersicht vor der Abnahme für die gewählte Person (#1122): dieselben Bausteine wie vor der
     * Freigabe, dazu die Folge der Abnahme. Ohne Vertrag oder Monat geht es zurück zur Abnahme; wer
     * den Vertrag nicht abnehmen darf, bekommt 403 (Service) — auch für den eigenen.
     */
    @GetMapping("/accept/review")
    public String acceptReview(@RequestParam(required = false) Long contractId,
                               @RequestParam(required = false) String until,
                               @RequestParam(required = false) String view,
                               Model model) {
        var month = ReviewPage.month(until);
        if (contractId == null || month.isEmpty()
            || employeecontractService.getEmployeecontractById(contractId) == null) {
            return "redirect:/acceptance";
        }
        var effectiveView = ReviewLinks.viewOf(view);
        var review = releaseService.reviewAcceptance(contractId, month.get().atEndOfMonth());
        var links = ReviewLinks.of(ACCEPT_REVIEW_PATH, contractId, month.get(), effectiveView,
            "/acceptance/accept", "/acceptance");
        ReviewPage.addReview(model, review, links, effectiveView, errorCodeViewHelper);
        model.addAttribute("section", "backoffice");
        model.addAttribute("subSection", "acceptance");
        model.addAttribute("pageTitle", messages.getMessage("main.release.review.title.accept.text"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.backoffice.text"));
        return ReviewPage.ACCEPTANCE_VIEW_NAME;
    }

    /**
     * Nimmt genau den Zeitraum ab, den die Übersicht gezeigt hat (#1122). Danach geht es ohne
     * Filterparameter zurück: die Auswahl der Abnahme ist gemerkt, und Speichern ändert den Filter
     * nicht (ADR-0023). Scheitert es, geht es zurück in die Übersicht derselben Person; eine fehlende
     * Berechtigung wird nicht abgefangen, sie endet als 403 auf der Fehlerseite.
     */
    @PostMapping("/accept")
    public String accept(@RequestParam long contractId,
                         @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate periodBegin,
                         @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate periodEnd,
                         @RequestParam(required = false) String view,
                         RedirectAttributes redirectAttributes) {
        try {
            releaseService.acceptTimereports(contractId, periodBegin, periodEnd);
            redirectAttributes.addFlashAttribute("toastSuccess",
                ReviewPage.acceptedMessage(messages, periodBegin, periodEnd));
            return "redirect:/acceptance";
        } catch (BusinessRuleException | InvalidDataException ex) {
            redirectAttributes.addFlashAttribute("toastError", ReviewPage.failureMessage(ex, errorCodeViewHelper,
                messages, "main.release.review.notaccepted.text"));
            return "redirect:" + ReviewLinks.of(ACCEPT_REVIEW_PATH, contractId, YearMonth.from(periodEnd),
                ReviewLinks.viewOf(view), "/acceptance/accept", "/acceptance").currentUrl();
        }
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
                return employeecontractService.getVisibleEmployeeContractsForAuthorizedUser();
            }
            return employeecontractService.getTeamContractsIncludingExpired(supervisorId);
        }
        return employeecontractService.getTeamContractsIncludingExpired(loginEmployee.getId());
    }

    private String defaultReleaseDateStr(Employeecontract contract) {
        if (contract == null) return "";
        LocalDate rd = contract.getReportReleaseDate();
        LocalDate defaultDate = rd == null ? contract.getValidFrom() : addMonths(rd, 1);
        return monthStr(min(defaultDate, contract.getValidUntil()));
    }

    /**
     * Der Monat, den die Abnahme vorschlägt (#1122): der der Freigabe — abgenommen wird, was
     * freigegeben ist. Bis dahin war es der Monat der letzten Abnahme, und dessen Übersicht wäre leer.
     * Ist noch nichts freigegeben, bleibt der frühere Vorschlag; die Übersicht sagt dann, warum es
     * nichts abzunehmen gibt.
     */
    private static String defaultAcceptanceDateStr(Employeecontract contract) {
        if (contract == null) return "";
        if (contract.getReportReleaseDate() != null) return monthStr(contract.getReportReleaseDate());
        LocalDate ad = contract.getReportAcceptanceDate();
        LocalDate defaultDate = ad == null ? contract.getValidFrom() : ad;
        return monthStr(min(defaultDate, contract.getValidUntil()));
    }

    /**
     * Der späteste Monat, den die Abnahme anbietet (#1122): der der Freigabe, denn weiter reicht keine
     * Abnahme. Ohne Freigabe das Vertragsende, wie bisher.
     */
    private static String acceptanceMaxMonthStr(Employeecontract contract) {
        if (contract != null && contract.getReportReleaseDate() != null) return monthStr(contract.getReportReleaseDate());
        return lastMonthStr(contract);
    }

    /** Der letzte Monat, in dem es etwas freizugeben oder abzunehmen gibt (#324). */
    private static String lastMonthStr(Employeecontract contract) {
        if (contract == null || contract.getValidUntil() == null) return null;
        return monthStr(contract.getValidUntil());
    }

    private static String monthStr(LocalDate date) {
        return YearMonth.from(date).toString();
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
