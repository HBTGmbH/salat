package org.tb.management.controller;

import static org.tb.common.util.DateUtils.today;
import static org.tb.dailyreport.viewhelper.VacationViewHelper.calculateAndSetVacations;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.OvertimeService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.viewhelper.VacationViewHelper;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.service.EmployeeorderService;

@Controller
@RequestMapping("/my-accounts")
@RequiredArgsConstructor
public class MyAccountsController {

    @Getter
    @AllArgsConstructor
    public static class TrainingBooking {
        private final String date;
        private final String orderSign;
        private final String customer;
        private final String description;
        private final String duration;
    }

    private final OvertimeService overtimeService;
    private final EmployeeorderService employeeorderService;
    private final TimereportService timereportService;
    private final MessageSourceAccessor messageSourceAccessor;

    @GetMapping
    public String show(HttpSession session, Model model) {
        var contract = currentContract(session);
        var today = today();
        var currentYear = today.getYear();
        var yearStart = LocalDate.of(currentYear, 1, 1);

        model.addAttribute("pageTitle", messageSourceAccessor.getMessage("main.my.accounts.title"));
        model.addAttribute("section", "management");
        model.addAttribute("subSection", "my-accounts");
        model.addAttribute("sectionTitle", messageSourceAccessor.getMessage("main.general.mainmenu.management.text"));

        // --- Tab 1: Working time account ---
        populateWorkingTimeTab(model, contract, today, currentYear);

        // --- Tab 2: Vacation account ---
        populateVacationTab(session, model, contract, today, currentYear, yearStart);

        // --- Tab 3: Training ---
        populateTrainingTab(model, contract, today, yearStart);

        return "management/my-accounts";
    }

    private void populateWorkingTimeTab(Model model, Employeecontract contract, LocalDate today, int currentYear) {
        var overtimeStatus = overtimeService.calculateOvertime(contract.getId(), true);

        String balance = overtimeStatus.map(s -> DurationUtils.format(s.getTotal().getDuration())).orElse("0:00");
        boolean balanceIsNegative = overtimeStatus.map(s -> s.getTotal().isNegative()).orElse(false);
        String balanceColorClass = overtimeStatus.map(s -> {
            if (s.getTotal() == null) return "success";
            long hours = s.getTotal().getDuration().toHours();
            long signedHours = s.getTotal().isNegative() ? -hours : hours;
            if (signedHours > 80 || signedHours < -40) return "danger";
            if (signedHours > 40 || signedHours < -20) return "warning";
            return "success";
        }).orElse("success");

        // Cumulative balance as of Dec 31 of the previous year
        int prevYear = currentYear - 1;
        LocalDate prevYearEnd = LocalDate.of(prevYear, 12, 31);
        String previousYearCarryover = "0:00";
        boolean previousYearCarryoverIsNegative = false;
        if (!contract.getValidFrom().isAfter(prevYearEnd)) {
            var prevYearOpt = overtimeService.calculateOvertime(
                    contract.getId(), contract.getValidFrom(), prevYearEnd);
            previousYearCarryover = prevYearOpt.map(DurationUtils::format).orElse("0:00");
            previousYearCarryoverIsNegative = prevYearOpt.map(Duration::isNegative).orElse(false);
        }

        // Bar chart: last 6 months in ascending order
        var overtimeReport = overtimeService.createDetailedReportForEmployee(contract.getId());
        var monthsDesc = overtimeReport.getMonths(); // already sorted descending
        int count = Math.min(6, monthsDesc.size());
        var chartLabels = new ArrayList<String>();
        var chartActual = new ArrayList<Double>();
        var chartTarget = new ArrayList<Double>();
        for (int i = count - 1; i >= 0; i--) {
            var m = monthsDesc.get(i);
            chartLabels.add(m.getYearMonth().getMonth().getDisplayName(TextStyle.SHORT, Locale.GERMAN)
                    + " '" + String.valueOf(m.getYearMonth().getYear()).substring(2));
            chartActual.add(Math.round(m.getActual().toMinutes() / 6.0) / 10.0);
            chartTarget.add(Math.round(m.getTarget().toMinutes() / 6.0) / 10.0);
        }

        model.addAttribute("balance", balance);
        model.addAttribute("balanceIsNegative", balanceIsNegative);
        model.addAttribute("balanceColorClass", balanceColorClass);
        model.addAttribute("previousYearCarryover", previousYearCarryover);
        model.addAttribute("previousYearCarryoverIsNegative", previousYearCarryoverIsNegative);
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartActual", chartActual);
        model.addAttribute("chartTarget", chartTarget);
    }

    private void populateVacationTab(HttpSession session, Model model, Employeecontract contract,
            LocalDate today, int currentYear, LocalDate yearStart) {
        calculateAndSetVacations(session, contract, employeeorderService, timereportService);
        @SuppressWarnings("unchecked")
        var vacations = (List<VacationViewHelper>) session.getAttribute("vacations");

        double annualEntitlementDays = 0;
        double previousYearCarryoverDays = 0;
        double takenDays = 0;
        double plannedDays = 0;
        int usedPercent = 0;

        long dailyWorkingMinutes = contract.getDailyWorkingTime().toMinutes();
        var vacationOrders = employeeorderService.getVacationEmployeeOrders(contract.getId());
        var vacationMonthLabels = new ArrayList<String>();
        var vacationMonthDays = new ArrayList<Double>();

        if (dailyWorkingMinutes > 0 && !vacationOrders.isEmpty()) {
            var currentYearSign = String.valueOf(currentYear);

            for (var order : vacationOrders) {
                Duration budget = order.getDebithours();
                if (budget == null || budget.isZero()) continue;
                double budgetDays = (double) budget.toMinutes() / dailyWorkingMinutes;
                if (currentYearSign.equals(order.getSuborder().getSign())) {
                    annualEntitlementDays += budgetDays;
                } else {
                    previousYearCarryoverDays += budgetDays;
                }
            }

            for (var order : vacationOrders) {
                long suborderId = order.getSuborder().getId();
                takenDays += (double) timereportService.getTotalDurationMinutesForSuborder(
                        suborderId, LocalDate.of(2000, 1, 1), today) / dailyWorkingMinutes;
                plannedDays += (double) timereportService.getTotalDurationMinutesForSuborder(
                        suborderId, today.plusDays(1), today.plusYears(2)) / dailyWorkingMinutes;
            }

            double totalBudget = annualEntitlementDays + previousYearCarryoverDays;
            usedPercent = totalBudget > 0
                    ? (int) Math.min(100, (takenDays + plannedDays) * 100 / totalBudget) : 0;

            // Monthly breakdown for current year (past dates only)
            var monthMinutes = new TreeMap<Integer, Long>();
            for (int m = 1; m <= today.getMonthValue(); m++) monthMinutes.put(m, 0L);
            for (var order : vacationOrders) {
                var reports = timereportService.getTimereportsByDatesAndSuborderId(
                        yearStart, today, order.getSuborder().getId());
                for (var report : reports) {
                    monthMinutes.merge(report.getReferenceday().getMonthValue(),
                            report.getDuration().toMinutes(), Long::sum);
                }
            }
            for (var entry : monthMinutes.entrySet()) {
                vacationMonthLabels.add(LocalDate.of(currentYear, entry.getKey(), 1)
                        .getMonth().getDisplayName(TextStyle.SHORT, Locale.GERMAN));
                vacationMonthDays.add(Math.round((double) entry.getValue() / dailyWorkingMinutes * 10) / 10.0);
            }
        }

        double remainingDays = Math.max(0,
                annualEntitlementDays + previousYearCarryoverDays - takenDays - plannedDays);

        model.addAttribute("vacations", vacations);
        model.addAttribute("annualEntitlementDays", String.format(Locale.GERMAN, "%.1f", annualEntitlementDays));
        model.addAttribute("previousYearCarryoverDays", String.format(Locale.GERMAN, "%.1f", previousYearCarryoverDays));
        model.addAttribute("takenDays", String.format(Locale.GERMAN, "%.1f", takenDays));
        model.addAttribute("plannedDays", String.format(Locale.GERMAN, "%.1f", plannedDays));
        model.addAttribute("remainingDays", String.format(Locale.GERMAN, "%.1f", remainingDays));
        model.addAttribute("vacationUsedPercent", usedPercent);
        model.addAttribute("vacationMonthLabels", vacationMonthLabels);
        model.addAttribute("vacationMonthDays", vacationMonthDays);
    }

    private void populateTrainingTab(Model model, Employeecontract contract, LocalDate today, LocalDate yearStart) {
        var contractStart = contract.getValidFrom().isAfter(yearStart) ? contract.getValidFrom() : yearStart;
        var allReports = timereportService.getTimereportsByDatesAndEmployeeContractId(
                contract.getId(), contractStart, today);

        var trainingReports = allReports.stream().filter(TimereportDTO::isTraining).toList();
        long totalTrainingMinutes = trainingReports.stream()
                .mapToLong(t -> t.getDuration().toMinutes()).sum();
        long totalWorkMinutes = allReports.stream()
                .mapToLong(t -> t.getDuration().toMinutes()).sum();
        int trainingSharePercent = totalWorkMinutes > 0
                ? (int) Math.round(totalTrainingMinutes * 100.0 / totalWorkMinutes) : 0;

        var bookings = trainingReports.stream()
                .sorted(Comparator.comparing(TimereportDTO::getReferenceday).reversed())
                .map(t -> new TrainingBooking(
                        t.getReferenceday().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                        t.getCompleteOrderSign(),
                        t.getCustomerShortname(),
                        t.getTaskdescription(),
                        DurationUtils.format(t.getDuration())))
                .toList();

        // Monthly bar chart
        var monthMinutes = new TreeMap<String, Long>();
        var cur = contractStart.withDayOfMonth(1);
        while (!cur.isAfter(today)) {
            monthMinutes.put(cur.getYear() + "-" + String.format("%02d", cur.getMonthValue()), 0L);
            cur = cur.plusMonths(1);
        }
        for (var report : trainingReports) {
            var key = report.getReferenceday().getYear() + "-"
                    + String.format("%02d", report.getReferenceday().getMonthValue());
            monthMinutes.merge(key, report.getDuration().toMinutes(), Long::sum);
        }
        var trainingChartLabels = new ArrayList<String>();
        var trainingChartHours = new ArrayList<Double>();
        for (var entry : monthMinutes.entrySet()) {
            var parts = entry.getKey().split("-");
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            trainingChartLabels.add(LocalDate.of(y, m, 1).getMonth()
                    .getDisplayName(TextStyle.SHORT, Locale.GERMAN) + " '" + String.valueOf(y).substring(2));
            trainingChartHours.add(Math.round(entry.getValue() / 6.0) / 10.0);
        }

        model.addAttribute("totalTrainingHours", DurationUtils.format(Duration.ofMinutes(totalTrainingMinutes)));
        model.addAttribute("trainingSharePercent", trainingSharePercent);
        model.addAttribute("trainingBookings", bookings);
        model.addAttribute("trainingChartLabels", trainingChartLabels);
        model.addAttribute("trainingChartHours", trainingChartHours);
    }

    private Employeecontract currentContract(HttpSession session) {
        var contract = (Employeecontract) session.getAttribute("currentEmployeeContract");
        if (contract == null) {
            contract = (Employeecontract) session.getAttribute("loginEmployeeContract");
        }
        return contract;
    }
}
