package org.tb.dailyreport.controller;

import static org.tb.common.util.DateUtils.formatMonth;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.service.DailyService;
import org.tb.dailyreport.service.MatrixService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@Controller
@RequestMapping("/dailyreport/daily")
@RequiredArgsConstructor
@Authorized
public class DailyController {

    private final DailyService dailyService;
    private final MatrixService matrixService;
    private final TimereportService timereportService;
    private final WorkingdayService workingdayService;
    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;

    @GetMapping
    public String show(
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long employeeContractId,
            Model model) {

        var today = today();
        var loginEmployee = employeeService.getLoginEmployee();
        var contracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today);
        var myContract = employeecontractService.getCurrentContract(loginEmployee.getId()).orElse(null);
        long ecId = employeeContractId != null ? employeeContractId
            : (myContract != null ? myContract.getId() : -1L);

        String effectiveMode = (mode != null && mode.equals("list")) ? "list" : "daily";

        model.addAttribute("employeecontracts", contracts);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("mode", effectiveMode);
        model.addAttribute("section", "dailyreport");
        model.addAttribute("subSection", "daily");
        model.addAttribute("containerClass", "container-xl");
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.timereports.text"));
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.daily.text"));

        LocalDate targetDate = date != null ? date : today;

        if ("list".equals(effectiveMode)) {
            int targetMonth = month != null ? month : today.getMonthValue();
            int targetYear = year != null ? year : today.getYear();
            YearMonth yearMonth = YearMonth.of(targetYear, targetMonth);
            YearMonth prev = yearMonth.minusMonths(1);
            YearMonth next = yearMonth.plusMonths(1);
            String monthKey = "main.timereport.select.month." + formatMonth(yearMonth.atDay(1)).toLowerCase() + ".text";

            if (ecId > 0) {
                model.addAttribute("listData", dailyService.buildListView(yearMonth, ecId));
            }
            model.addAttribute("yearMonth", yearMonth);
            model.addAttribute("prevMonth", prev.getMonthValue());
            model.addAttribute("prevYear", prev.getYear());
            model.addAttribute("nextMonth", next.getMonthValue());
            model.addAttribute("nextYear", next.getYear());
            model.addAttribute("title", messages.getMessage(monthKey) + " " + targetYear);
        } else {
            YearMonth yearMonth = YearMonth.from(targetDate);
            LocalDate prev = targetDate.minusDays(1);
            LocalDate next = targetDate.plusDays(1);

            if (ecId > 0) {
                var dailyData = dailyService.buildDailyView(targetDate, ecId);
                model.addAttribute("dailyData", dailyData);
                model.addAttribute("weekStripData", dailyData.weekStrip());
            }
            model.addAttribute("yearMonth", yearMonth);
            model.addAttribute("date", targetDate);
            model.addAttribute("prevDate", prev);
            model.addAttribute("nextDate", next);
            model.addAttribute("title", targetDate.toString());
        }

        return "dailyreport/daily";
    }

    @PostMapping("/workingday")
    @PreAuthorize("isAuthenticated()")
    public String saveWorkingday(
            @RequestParam Long employeeContractId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int starttimehour,
            @RequestParam(defaultValue = "0") int starttimeminute,
            @RequestParam(defaultValue = "0") int breakhours,
            @RequestParam(defaultValue = "0") int breakminutes,
            @RequestParam(defaultValue = "false") boolean notWorked,
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            var contract = employeecontractService.getEmployeecontractById(employeeContractId);
            Workingday workingday = workingdayService.getWorkingday(employeeContractId, date);
            if (workingday == null) {
                workingday = new Workingday();
                workingday.setEmployeecontract(contract);
                workingday.setRefday(date);
            }
            if (notWorked) {
                workingday.setType(Workingday.WorkingDayType.NOT_WORKED);
                workingday.setStarttimehour(0);
                workingday.setStarttimeminute(0);
                workingday.setBreakhours(0);
                workingday.setBreakminutes(0);
            } else {
                workingday.setType(Workingday.WorkingDayType.WORKED);
                workingday.setStarttimehour(starttimehour);
                workingday.setStarttimeminute(starttimeminute);
                workingday.setBreakhours(breakhours);
                workingday.setBreakminutes(breakminutes);
            }
            workingdayService.upsertWorkingday(workingday);
            if ("true".equals(request.getHeader("HX-Request"))) {
                model.addAttribute("weekStripData", dailyService.buildWeekStrip(date, employeeContractId));
                model.addAttribute("selectedContractId", employeeContractId);
                return "dailyreport/daily :: weekStrip";
            }
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.daily.workingday.save.success.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream()
                    .map(Object::toString).findFirst().orElse("Error"));
        }
        return "redirect:/dailyreport/daily?mode=daily&date=" + date + "&employeeContractId=" + employeeContractId;
    }

    @PostMapping("/delete-timereport")
    @PreAuthorize("isAuthenticated()")
    public String deleteTimereport(
            @RequestParam Long timereportId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String mode,
            @RequestParam Long employeeContractId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            RedirectAttributes redirectAttributes) {
        try {
            timereportService.deleteTimereportById(timereportId);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.daily.timereport.delete.success.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream()
                    .map(Object::toString).findFirst().orElse("Error"));
        }
        if ("list".equals(mode) && month != null && year != null) {
            return "redirect:/dailyreport/daily?mode=list&month=" + month + "&year=" + year + "&employeeContractId=" + employeeContractId;
        }
        return "redirect:/dailyreport/daily?mode=daily&date=" + date + "&employeeContractId=" + employeeContractId;
    }

    @PostMapping("/fill-not-worked")
    @PreAuthorize("isAuthenticated()")
    public String fillNotWorked(
            @RequestParam Long employeeContractId,
            @RequestParam Integer month,
            @RequestParam Integer year,
            RedirectAttributes redirectAttributes) {
        try {
            matrixService.fillNotWorked(YearMonth.of(year, month), employeeContractId);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.matrix.fillnotworked.success.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream()
                    .map(Object::toString).findFirst().orElse("Error"));
        }
        return "redirect:/dailyreport/daily?mode=list&month=" + month + "&year=" + year + "&employeeContractId=" + employeeContractId;
    }
}
