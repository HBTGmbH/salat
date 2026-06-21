package org.tb.dailyreport.controller;

import static java.lang.Boolean.TRUE;
import static org.tb.common.util.DateUtils.getWorkingDayDistance;
import static org.tb.common.util.DateUtils.today;
import static org.tb.dailyreport.viewhelper.OvertimeViewHelper.calculateAndSetOvertime;
import static org.tb.dailyreport.viewhelper.VacationViewHelper.calculateAndSetVacations;

import jakarta.servlet.http.HttpSession;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.Comparator;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.LocalDateRange;
import org.tb.common.util.DurationUtils;
import org.tb.common.web.UiState;
import org.tb.employee.controller.EmployeeUiStateKeyContributor;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.OvertimeService;
import org.tb.dailyreport.service.PublicholidayService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.service.EmployeeorderService;

@Controller
@RequestMapping("/dailyreport/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    @Getter
    @AllArgsConstructor
    static class OrderHourSummary {
        private final String customer;
        private final String orderSign;
        private final String hoursString;
        private final long minutes;
    }

    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final AuthService authService;
    private final OvertimeService overtimeService;
    private final EmployeeorderService employeeorderService;
    private final TimereportService timereportService;
    private final PublicholidayService publicholidayService;
    private final MessageSourceAccessor messageSourceAccessor;
    private final AuthorizedUser authorizedUser;
    private final AuthorizedEmployee authorizedEmployee;
    private final UiState uiState;

    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        var employeecontract = currentContract();
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
        session.setAttribute("releasedUntil", employeecontract.getReportReleaseDate());
        session.setAttribute("acceptedUntil", employeecontract.getReportAcceptanceDate());
        session.setAttribute("warnings", warnings);
        session.setAttribute("warningsPresent", !warnings.isEmpty());

        boolean displayEmployeeInfo = !TRUE.equals(employeecontract.getFreelancer());

        model.addAttribute("pageTitle", messageSourceAccessor.getMessage("main.general.mainmenu.dashboard.text"));
        model.addAttribute("section", "dailyreport");
        model.addAttribute("subSection", "dashboard");
        model.addAttribute("sectionTitle", messageSourceAccessor.getMessage("main.general.mainmenu.timereports.text"));
        model.addAttribute("employeecontracts", employeecontracts);
        model.addAttribute("loginEmployees", loginEmployees);
        model.addAttribute("currentEmployeeContractId", employeecontract.getId());
        model.addAttribute("currentLoginEmployeeId", authorizedEmployee.getEmployeeId());
        model.addAttribute("effectiveLoginSign", authorizedUser.getEffectiveLoginSign());
        model.addAttribute("displayEmployeeInfo", displayEmployeeInfo);
        model.addAttribute("warnings", warnings);
        model.addAttribute("releasedUntil", employeecontract.getReportReleaseDate());
        model.addAttribute("releaseColorClass", employeecontract.getReleaseWarning() ? "danger" : "success");
        model.addAttribute("acceptedUntil", employeecontract.getReportAcceptanceDate());
        model.addAttribute("acceptanceColorClass", employeecontract.getAcceptanceWarning() ? "danger" : "success");
        model.addAttribute("overtime", session.getAttribute("overtime"));
        model.addAttribute("overtimeIsNegative", session.getAttribute("overtimeIsNegative"));
        model.addAttribute("overtimeColorClass", overtimeColorClass(employeecontract.getId()));
        model.addAttribute("monthlyOvertime", session.getAttribute("monthlyOvertime"));
        model.addAttribute("monthlyOvertimeIsNegative", session.getAttribute("monthlyOvertimeIsNegative"));
        model.addAttribute("monthlyOvertimeColorClass", monthlyOvertimeColorClass(employeecontract.getId()));
        model.addAttribute("overtimeMonth", session.getAttribute("overtimeMonth"));
        model.addAttribute("vacations", session.getAttribute("vacations"));

        calculateEmployeeInfo(model, employeecontract);

        return "dailyreport/dashboard";
    }

    private void calculateEmployeeInfo(Model model, Employeecontract employeecontract) {
        var todayDate = today();
        var weekStart = todayDate.with(DayOfWeek.MONDAY);
        var weekEnd = weekStart.plusWeeks(1);
        var monthStart = todayDate.withDayOfMonth(1);
        var monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

        var recentReports = timereportService.getTimereportsByDatesAndEmployeeContractId(
            employeecontract.getId(), todayDate.minusDays(60), monthEnd);

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

        // Last log
        var lastLogOpt = recentReports.stream()
            .map(TimereportDTO::getReferenceday)
            .max(Comparator.naturalOrder());
        int businessDaysLagging = lastLogOpt.map(d -> {
            if (!d.isBefore(todayDate)) return 0;
            long rawDays = Math.max(0L, getWorkingDayDistance(d, todayDate) - 1);
            long holidaysBetween = publicholidayService
                .getPublicHolidaysBetween(d.plusDays(1), todayDate).stream()
                .filter(h -> h.getRefdate().getDayOfWeek() != DayOfWeek.SATURDAY
                          && h.getRefdate().getDayOfWeek() != DayOfWeek.SUNDAY)
                .count();
            return (int) Math.max(0L, rawDays - holidaysBetween);
        }).orElse(99);

        // Hours by order this month, sorted by logged hours desc
        var orderHours = recentReports.stream()
            .filter(r -> month.contains(r.getReferenceday()))
            .collect(Collectors.groupingBy(TimereportDTO::getCompleteOrderSign, Collectors.toList()))
            .entrySet().stream()
            .map(e -> {
                var sum = e.getValue().stream()
                    .map(TimereportDTO::getDuration)
                    .reduce(Duration.ZERO, Duration::plus);
                var customer = e.getValue().getFirst().getCustomerShortname();
                return new OrderHourSummary(customer, e.getKey(), DurationUtils.format(sum), sum.toMinutes());
            })
            .sorted(Comparator.comparingLong(OrderHourSummary::getMinutes).reversed())
            .toList();

        model.addAttribute("weekLogged", DurationUtils.format(weekLogged));
        model.addAttribute("weekTarget", DurationUtils.format(weekTarget));
        model.addAttribute("weekPercent", weekPercent);
        model.addAttribute("weekPercentCapped", Math.min(100, weekPercent));
        model.addAttribute("monthLogged", DurationUtils.format(monthLogged));
        model.addAttribute("monthTarget", DurationUtils.format(monthTarget));
        model.addAttribute("monthPercent", monthPercent);
        model.addAttribute("monthPercentCapped", Math.min(100, monthPercent));
        model.addAttribute("lastLogDate", lastLogOpt.orElse(null));
        model.addAttribute("businessDaysLagging", businessDaysLagging);
        model.addAttribute("lastLogIsLagging", businessDaysLagging > 1);
        model.addAttribute("orderHours", orderHours);
    }

    @PostMapping(params = "task=refresh")
    public String refresh(@RequestParam Long employeeContractId, HttpSession session) {
        var employeecontract = employeecontractService.getEmployeecontractById(employeeContractId);
        session.setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
        session.setAttribute("currentEmployeeContract", employeecontract);
        return "redirect:/dailyreport/dashboard";
    }

    @PostMapping(params = "task=switch-login")
    public String switchLogin(@RequestParam Long loginEmployeeId) {
        var switchedToEmployee = employeeService.getEmployeeById(loginEmployeeId);
        authService.switchLogin(switchedToEmployee.getLoginname());
        return "redirect:/dailyreport/dashboard";
    }

    private Employeecontract currentContract() {
        Long contractId = uiState.getLong(EmployeeUiStateKeyContributor.SELECTED_CONTRACT);
        if (contractId != null && contractId > 0) {
            var contract = employeecontractService.getEmployeecontractById(contractId);
            if (contract != null) return contract;
        }
        var loginEmployee = employeeService.getLoginEmployee();
        return employeecontractService.getCurrentContract(loginEmployee.getId())
            .orElseThrow(() -> new IllegalStateException("No current contract for login employee"));
    }

    /** Returns "success", "warning", or "danger" based on total overtime thresholds (in hours). */
    private String overtimeColorClass(long employeecontractId) {
        return overtimeService.calculateOvertime(employeecontractId, true)
            .map(status -> {
                if(status.getTotal() == null) return "success";
                long hours = status.getTotal().getDuration().toHours();
                long signedHours = status.getTotal().isNegative() ? -hours : hours;
                if (signedHours > 80 || signedHours < -40) return "danger";
                if (signedHours > 40 || signedHours < -20) return "warning";
                return "success";
            })
            .orElse("success");
    }

    /** Returns "success", "warning", or "danger" based on monthly overtime thresholds (in hours). */
    private String monthlyOvertimeColorClass(long employeecontractId) {
        return overtimeService.calculateOvertime(employeecontractId, true)
            .map(status -> {
                if(status.getCurrentMonth() == null) return "success";
                long hours = status.getCurrentMonth().getDuration().toHours();
                long signedHours = status.getTotal().isNegative() ? -hours : hours;
                if (signedHours > 30 || signedHours < -30) return "danger";
                if (signedHours > 15 || signedHours < -15) return "warning";
                return "success";
            })
            .orElse("success");
    }

}
