package de.hbt.salat.dailyreport.controller;

import static java.lang.Boolean.TRUE;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static de.hbt.salat.common.util.DateUtils.getWorkingDayDistance;
import static de.hbt.salat.common.util.DateUtils.today;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.common.util.DurationUtils;
import de.hbt.salat.dailyreport.domain.OvertimeStatus;
import de.hbt.salat.dailyreport.domain.OvertimeStatus.OvertimeStatusInfo;
import de.hbt.salat.dailyreport.domain.Publicholiday;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.domain.UnbookedWorkingDays;
import de.hbt.salat.dailyreport.domain.VacationInfo;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.service.MatrixService;
import de.hbt.salat.dailyreport.service.OvertimeService;
import de.hbt.salat.dailyreport.service.ReleaseService;
import de.hbt.salat.dailyreport.service.VacationService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.dailyreport.viewhelper.DashboardGrades;
import de.hbt.salat.dailyreport.viewhelper.OvertimeScale;
import de.hbt.salat.dailyreport.viewhelper.VacationViewHelper;
import de.hbt.salat.dailyreport.service.PublicholidayService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;

@Controller
@RequestMapping("/dailyreport/dashboard")
@RequiredArgsConstructor
@Authorized
public class DashboardController {

    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final OvertimeService overtimeService;
    private final VacationService vacationService;
    private final TimereportService timereportService;
    private final PublicholidayService publicholidayService;
    private final ReleaseService releaseService;
    private final MatrixService matrixService;
    private final WorkingdayService workingdayService;
    private final MessageSourceAccessor messageSourceAccessor;
    @GetMapping
    public String dashboard(@RequestParam(required = false) Long fEmployeeContractId, Model model) {
        var employeecontract = currentContract(fEmployeeContractId);

        var overtimeStatus = overtimeService.calculateOvertime(employeecontract.getId(), false);
        var vacations = vacationService.getVacations(employeecontract).stream()
            .filter(this::isMeaningFullVacationInfo)
            .map(info -> VacationViewHelper.from(employeecontract, info))
            .toList();

        boolean displayEmployeeInfo = !TRUE.equals(employeecontract.getFreelancer());

        model.addAttribute("pageTitle", messageSourceAccessor.getMessage("main.general.mainmenu.dashboard.text"));
        model.addAttribute("section", "dailyreport");
        model.addAttribute("subSection", "dashboard");
        model.addAttribute("sectionTitle", messageSourceAccessor.getMessage("main.general.mainmenu.timereports.text"));
        model.addAttribute("displayEmployeeInfo", displayEmployeeInfo);
        model.addAttribute("releasedUntil", employeecontract.getReportReleaseDate());
        model.addAttribute("releaseColorClass",
            DashboardGrades.release(employeecontract.getReportReleaseDate(), employeecontract.getReleaseWarning()));
        model.addAttribute("acceptedUntil", employeecontract.getReportAcceptanceDate());
        model.addAttribute("acceptanceColorClass", employeecontract.getAcceptanceWarning() ? "danger" : "success");
        // die Schwellen gelten fuer 40 Wochenstunden und werden auf den Vertrag umgerechnet (#1175)
        addOvertimeAttributes(model, overtimeStatus, OvertimeScale.TOTAL.forContract(employeecontract),
            OvertimeScale.CURRENT_MONTH.forContract(employeecontract));
        model.addAttribute("vacations", vacations);
        // the hint follows the contract the page shows, and its links name it (#1124)
        model.addAttribute("unbookedDays", releaseService.getUnbookedWorkingDaysOfPreviousWeek(employeecontract.getId()));
        model.addAttribute("shownContractId", employeecontract.getId());
        // die Buchungsliste filtert nach Person, nicht nach Vertrag (#1175)
        model.addAttribute("shownEmployeeId", employeecontract.getEmployee().getId());
        // the matrix of the running month for the contract resolved above - never for the id from
        // the request, which may name a contract the login is not allowed to read (#1134, #878)
        var matrixMonth = YearMonth.from(today());
        model.addAttribute("matrixMonth", matrixMonth);
        model.addAttribute("matrixData", matrixService.buildMatrix(matrixMonth, employeecontract.getId()));
        model.addAttribute("showBeginBreakEnd", displayEmployeeInfo);

        calculateEmployeeInfo(model, employeecontract);

        return "dailyreport/dashboard";
    }

    private boolean isMeaningFullVacationInfo(VacationInfo info) {
        return !info.budget().isZero() || info.usedVacationMinutes() > 0;
    }

    private void calculateEmployeeInfo(Model model, Employeecontract employeecontract) {
        var todayDate = today();
        var weekStart = todayDate.with(DayOfWeek.MONDAY);
        // LocalDateRange schliesst beide Enden ein: der Sonntag ist der letzte Tag der Woche
        var weekEnd = weekStart.plusDays(6);
        var monthStart = todayDate.withDayOfMonth(1);
        var monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

        var windowStart = todayDate.minusDays(60);
        var recentReports = timereportService.getTimereportsByDatesAndEmployeeContractId(
            employeecontract.getId(), windowStart, monthEnd);

        // Public holidays (weekdays only)
        long weekPublicHolidays = publicholidayService
            .getPublicHolidaysBetween(weekStart, weekEnd).stream()
            .filter(h -> h.getRefdate().getDayOfWeek() != DayOfWeek.SATURDAY
                      && h.getRefdate().getDayOfWeek() != DayOfWeek.SUNDAY)
            .count();
        long monthPublicHolidays = publicholidayService
            .getPublicHolidaysBetween(monthStart, monthEnd).stream()
            .filter(h -> h.getRefdate().getDayOfWeek() != DayOfWeek.SATURDAY
                      && h.getRefdate().getDayOfWeek() != DayOfWeek.SUNDAY)
            .count();

        // Week hours
        var week = new LocalDateRange(weekStart, weekEnd);
        var weekLogged =recentReports.stream()
            .filter(r -> week.contains(r.getReferenceday()))
            .map(TimereportDTO::getDuration)
            .reduce(Duration.ZERO, Duration::plus);
        var weekTarget = employeecontract.getDailyWorkingTime().multipliedBy(Math.max(0, 5 - weekPublicHolidays));
        int weekPercent = weekTarget.isZero() ? 0
            : (int) (weekLogged.toMinutes() * 100 / weekTarget.toMinutes());

        // Month hours
        var month = new LocalDateRange(monthStart, monthEnd);
        var monthLogged = recentReports.stream()
            .filter(r -> month.contains(r.getReferenceday()))
            .map(TimereportDTO::getDuration)
            .reduce(Duration.ZERO, Duration::plus);
        long totalWorkingDaysInMonth = getWorkingDayDistance(monthStart, monthEnd);
        var monthTarget = employeecontract.getDailyWorkingTime()
            .multipliedBy(Math.max(0, totalWorkingDaysInMonth - monthPublicHolidays));
        int monthPercent = monthTarget.isZero() ? 0
            : (int) (monthLogged.toMinutes() * 100 / monthTarget.toMinutes());

        // Last log: geplante Buchungen in der Zukunft verdecken keinen Rueckstand bis heute
        var lastLogOpt = recentReports.stream()
            .map(TimereportDTO::getReferenceday)
            .filter(day -> !day.isAfter(todayDate))
            .max(Comparator.naturalOrder());
        // ohne Buchung im Fenster zaehlt der Rueckstand ab Vertragsbeginn: ein neuer Vertrag ist
        // nicht vom ersten Tag an ueberfaellig
        var openFrom = lastLogOpt.map(day -> day.plusDays(1))
            .orElseGet(() -> DateUtils.max(windowStart, employeecontract.getValidFrom()));
        var openDays = openWorkingDays(employeecontract, openFrom, todayDate);

        model.addAttribute("weekStart", weekStart);
        model.addAttribute("weekEnd", weekEnd);
        model.addAttribute("weekColorClass", DashboardGrades.progress(weekPercent));
        model.addAttribute("monthStart", monthStart);
        model.addAttribute("monthEnd", monthEnd);
        model.addAttribute("monthColorClass", DashboardGrades.progress(monthPercent));
        model.addAttribute("weekLogged", DurationUtils.format(weekLogged));
        model.addAttribute("weekTarget", DurationUtils.format(weekTarget));
        model.addAttribute("weekPercent", weekPercent);
        model.addAttribute("weekPercentCapped", Math.min(100, weekPercent));
        model.addAttribute("monthLogged", DurationUtils.format(monthLogged));
        model.addAttribute("monthTarget", DurationUtils.format(monthTarget));
        model.addAttribute("monthPercent", monthPercent);
        model.addAttribute("monthPercentCapped", Math.min(100, monthPercent));
        model.addAttribute("lastLogDate", lastLogOpt.orElse(null));
        model.addAttribute("lastLogDaysAgo", lastLogOpt.map(day -> DAYS.between(day, todayDate)).orElse(0L));
        model.addAttribute("openWorkingDays", openDays.size());
        model.addAttribute("lastLogColorClass", DashboardGrades.lastBooking(openDays.size()));
        // der Link der Karte fuehrt zum ersten offenen Arbeitstag, ohne Rueckstand zu heute
        model.addAttribute("nextBookingDate", openDays.isEmpty() ? todayDate : openDays.getFirst());
    }

    /* Die offenen Arbeitstage von from bis einschliesslich heute, nach derselben Regel wie der
       Hinweis auf die Vorwoche (UnbookedWorkingDays): ein als nicht gearbeitet markierter Tag ist
       kein vergessener Buchungstag. from liegt hinter der letzten Buchung bis heute, dazwischen ist
       also nichts gebucht und die Menge der gebuchten Tage leer. Wer den Vertrag hier sehen darf,
       darf auch seine Arbeitstage lesen - beide Regeln lassen Manager, die Person selbst und die
       zustaendige People Lead zu. */
    private List<LocalDate> openWorkingDays(Employeecontract employeecontract, LocalDate from, LocalDate today) {
        if (from.isAfter(today)) return List.of();
        var workingDays = workingdayService
            .getWorkingdaysByEmployeeContractId(employeecontract.getId(), from, today).stream()
            .collect(toMap(Workingday::getRefday, identity()));
        var publicHolidays = publicholidayService.getPublicHolidaysBetween(from, today).stream()
            .map(Publicholiday::getRefdate)
            .collect(toSet());
        return UnbookedWorkingDays.between(from, today, employeecontract, Set.of(), workingDays, publicHolidays);
    }

    @PostMapping(params = "task=refresh")
    public String refresh(@RequestParam Long fEmployeeContractId) {
        return "redirect:/dailyreport/dashboard";
    }

    /* Ein Vertrag aus fEmployeeContractId, den die angemeldete Person nicht lesen darf, faellt auf
       ihren eigenen aktuellen Vertrag zurueck statt mit 403 zu antworten: UiState merkt sich den
       Wert, und ein gemerkter fremder Vertrag sperrte sonst die Startseite (#1134). */
    private Employeecontract currentContract(Long fEmployeeContractId) {
        if (fEmployeeContractId != null && fEmployeeContractId > 0) {
            try {
                var contract = employeecontractService.getEmployeecontractForView(fEmployeeContractId);
                if (contract != null) return contract;
            } catch (AuthorizationException e) {
                // not readable - fall through to the own contract
            }
        }
        var loginEmployee = employeeService.getLoginEmployee();
        return employeecontractService.getCurrentContract(loginEmployee.getId())
                .orElseThrow(() -> new IllegalStateException("No current contract for login employee"));
    }

    /* Ein Saldo, den es nicht gibt, bleibt ein leeres Feld - an ihm haengt die Sichtbarkeit der
       Zelle. Ein vorbelegtes "0:00" machte "kein Wert" von "Wert ist null" ununterscheidbar, sobald
       es im Modell stand, und die Monatszelle hing an der Monatsbezeichnung, die als Beschriftung
       nie leer ist: ohne Sollstunden oder mit einem Vertrag, der den laufenden Monat nicht
       beruehrt, behauptete sie damit einen Monatssaldo (#1031). Ein echtes 0:00 ist formatiert und
       deshalb weiterhin da. */
    static void addOvertimeAttributes(Model model, Optional<OvertimeStatus> overtimeStatus, OvertimeScale totalScale,
                                      OvertimeScale monthScale) {
        var total = overtimeStatus.map(OvertimeStatus::getTotal);
        var currentMonth = overtimeStatus.map(OvertimeStatus::getCurrentMonth);
        model.addAttribute("overtime", total.map(info -> DurationUtils.format(info.getDuration())).orElse(""));
        model.addAttribute("overtimeIsNegative", total.map(OvertimeStatusInfo::isNegative).orElse(false));
        model.addAttribute("overtimeColorClass", overtimeColorClass(overtimeStatus, totalScale));
        model.addAttribute("overtimeScale", totalScale);
        model.addAttribute("monthlyOvertime", currentMonth.map(info -> DurationUtils.format(info.getDuration())).orElse(""));
        model.addAttribute("monthlyOvertimeIsNegative", currentMonth.map(OvertimeStatusInfo::isNegative).orElse(false));
        model.addAttribute("monthlyOvertimeColorClass", monthlyOvertimeColorClass(overtimeStatus, monthScale));
        model.addAttribute("monthlyOvertimeScale", monthScale);
        model.addAttribute("overtimeMonth", currentMonth.map(info -> DateUtils.format(info.getBegin(), "yyyy-MM")).orElse(""));
    }

    /* Die Dauer ist bereits vorzeichenbehaftet (OvertimeService.toStatusInfo); isNegative daneben ist
       nur die Pfeilrichtung. Wer es hier ein zweites Mal anwendet, prueft bei Minusstunden die
       positive Seite der Skala - genau die Richtung, in der die Warnung gebraucht wird (#1030). */
    static String overtimeColorClass(Optional<OvertimeStatus> overtimeStatus, OvertimeScale scale) {
        return overtimeStatus
            .map(OvertimeStatus::getTotal)
            .map(info -> scale.colorClass(info.getDuration()))
            .orElse(OvertimeScale.NEUTRAL_COLOR_CLASS);
    }

    static String monthlyOvertimeColorClass(Optional<OvertimeStatus> overtimeStatus, OvertimeScale scale) {
        return overtimeStatus
            .map(OvertimeStatus::getCurrentMonth)
            .map(info -> scale.colorClass(info.getDuration()))
            .orElse(OvertimeScale.NEUTRAL_COLOR_CLASS);
    }

}
