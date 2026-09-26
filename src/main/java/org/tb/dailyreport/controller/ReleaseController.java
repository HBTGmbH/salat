package org.tb.dailyreport.controller;

import static org.tb.common.util.DateUtils.addMonths;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.min;
import static org.tb.common.util.DateUtils.today;

import java.time.LocalDate;
import java.time.YearMonth;
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
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.service.ReleaseService;
import org.tb.dailyreport.viewhelper.ReviewLinks;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

/**
 * Die eigene Freigabe (#760): der Monat wird gewählt, die Übersicht zeigt die Buchungen des
 * Zeitraums, und aus ihr heraus wird genau dieser Zeitraum freigegeben. Die Übersicht ersetzt den
 * Bestätigungsdialog, den das Freigeben bis dahin hatte (ADR-0027, Nachtrag #760).
 *
 * <p>Die Klasse trägt bewusst ein nacktes {@code @Authorized}, also nur „angemeldet", und nicht
 * {@code requireUnrestricted = true}, wie AGENTS.md es für Controller verlangt: auch Externe und
 * Praktikanten buchen und geben ihre eigenen Stunden frei. Ob jemand einen <em>bestimmten</em>
 * Vertrag sehen und freigeben darf — die eigene Person, die Geschäftsführung, die zuständige People
 * Lead oder eine Regel der Kategorie {@code RELEASE_TIMEREPORTS} —, entscheidet
 * {@link ReleaseService} je Vertrag, für die Übersicht wie für das Freigeben. Der Vertrag kommt aus
 * der gemerkten Auswahl {@code fEmployeeContractId} und damit aus der Anfrage; eine Prüfung hier
 * wäre deshalb keine.
 */
@Controller
@RequestMapping("/release")
@RequiredArgsConstructor
@Authorized
public class ReleaseController {

    private static final String REVIEW_PATH = "/release/review";

    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final ReleaseService releaseService;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;

    @GetMapping
    public String show(@RequestParam(required = false) Long fEmployeeContractId, Model model) {

        var effectiveContractId = effectiveContractId(fEmployeeContractId);
        var contract = employeecontractService.getEmployeecontractById(effectiveContractId);
        // Wer weder einen laufenden noch einen künftigen Vertrag hat, bekommt den Hinweis der
        // Seite statt einer Fehlerseite (#324).
        var employee = contract != null ? contract.getEmployee() : employeeService.getLoginEmployee();

        model.addAttribute("employee", employee);
        model.addAttribute("employeeContract", contract);
        model.addAttribute("selfReleaseDateStr", defaultReleaseDateStr(contract));
        model.addAttribute("lastMonthStr", lastMonthStr(contract));
        model.addAttribute("releasedUntil", contract != null ? format(contract.getReportReleaseDate()) : "");
        model.addAttribute("acceptedUntil", contract != null ? format(contract.getReportAcceptanceDate()) : "");
        model.addAttribute("section", "dailyreport");
        model.addAttribute("subSection", "release");
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.release.text"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.timereports.text"));
        return "dailyreport/release";
    }

    /**
     * Die Übersicht über den Zeitraum, den eine Freigabe bis zum Ende von {@code until} erfasst. Der
     * Vertrag kommt wie auf der Seite davor aus der gemerkten Auswahl. Ohne Monat oder ohne Vertrag
     * gibt es nichts zu zeigen, dann geht es zurück zur Auswahl.
     *
     * @param view {@code day} für die Sicht nach Tag, sonst nach Auftrag
     */
    @GetMapping("/review")
    public String review(@RequestParam(required = false) Long fEmployeeContractId,
                         @RequestParam(required = false) String until,
                         @RequestParam(required = false) String view,
                         Model model) {
        var month = ReviewPage.month(until);
        if (month.isEmpty()) {
            return "redirect:/release";
        }
        var contract = employeecontractService.getEmployeecontractById(effectiveContractId(fEmployeeContractId));
        if (contract == null) {
            return "redirect:/release";
        }
        var effectiveView = ReviewLinks.viewOf(view);
        var review = releaseService.reviewRelease(contract.getId(), month.get().atEndOfMonth());
        var links = ReviewLinks.of(REVIEW_PATH, null, month.get(), effectiveView, "/release", "/release");
        ReviewPage.addReview(model, review, links, effectiveView, errorCodeViewHelper);
        model.addAttribute("section", "dailyreport");
        model.addAttribute("subSection", "release");
        model.addAttribute("pageTitle", messages.getMessage("main.release.review.title.release.text"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.timereports.text"));
        return ReviewPage.VIEW_NAME;
    }

    /**
     * Gibt genau den Zeitraum frei, den die Übersicht gezeigt hat: Vertrag, Anfang und Ende kommen
     * aus ihrem Formular, nicht aus der gemerkten Auswahl, die sich in einem zweiten Fenster geändert
     * haben kann. Ob sich der Zeitraum inzwischen geändert hat, prüft der Service.
     *
     * <p>Scheitert es, geht es zurück in die Übersicht, die die Befunde zeigt — außer die gemerkte
     * Auswahl nennt inzwischen einen anderen Vertrag: dann zeigte die Übersicht eine andere Person,
     * und es geht zurück zur Auswahl. Eine fehlende Berechtigung wird nicht abgefangen, sie endet als
     * 403 auf der Fehlerseite.
     */
    @PostMapping
    public String release(@RequestParam(required = false) Long fEmployeeContractId,
                          @RequestParam long contractId,
                          @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate periodBegin,
                          @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate periodEnd,
                          @RequestParam(required = false) String view,
                          RedirectAttributes redirectAttributes) {
        try {
            releaseService.releaseTimereports(contractId, periodBegin, periodEnd);
            redirectAttributes.addFlashAttribute("toastSuccess",
                ReviewPage.releasedMessage(messages, periodBegin, periodEnd));
            return "redirect:/release";
        } catch (BusinessRuleException | InvalidDataException ex) {
            redirectAttributes.addFlashAttribute("toastError", ReviewPage.failureMessage(ex, errorCodeViewHelper,
                messages, "main.release.review.notreleased.text"));
            if (contractId != effectiveContractId(fEmployeeContractId)) {
                return "redirect:/release";
            }
            return "redirect:" + ReviewLinks.of(REVIEW_PATH, null, YearMonth.from(periodEnd),
                ReviewLinks.viewOf(view), "/release", "/release").currentUrl();
        }
    }

    private long effectiveContractId(Long fEmployeeContractId) {
        if (fEmployeeContractId != null && fEmployeeContractId > 0) {
            return fEmployeeContractId;
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
        return YearMonth.from(min(defaultDate, contract.getValidUntil())).toString();
    }

    /** Der letzte Monat, in dem es etwas freizugeben gibt (#324). */
    private String lastMonthStr(Employeecontract contract) {
        if (contract == null || contract.getValidUntil() == null) return null;
        return YearMonth.from(contract.getValidUntil()).toString();
    }

    /**
     * Das Ende des gewählten Monats; ein leeres Feld heißt „bis heute". Nur noch für die Abnahme,
     * bis auch sie eine Übersicht bekommt (#1122).
     */
    static LocalDate parseEndOfMonth(String s) {
        if (s == null || s.isBlank()) return today();
        return YearMonth.parse(s).atEndOfMonth();
    }
}
