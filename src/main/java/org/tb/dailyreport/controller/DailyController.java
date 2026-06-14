package org.tb.dailyreport.controller;

import static org.tb.common.util.DateUtils.formatMonth;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
                var form = new WorkingdayForm();
                form.setEmployeeContractId(ecId);
                form.setDate(targetDate);
                form.setNotWorked(dailyData.notWorked());
                form.setStartTime(dailyData.startTime());
                form.setBreakTime(dailyData.breakTime());
                model.addAttribute("workingdayForm", form);
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
            @ModelAttribute WorkingdayForm form,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        Long employeeContractId = form.getEmployeeContractId();
        LocalDate date = form.getDate();
        try {
            var contract = employeecontractService.getEmployeecontractById(employeeContractId);
            Workingday workingday = workingdayService.getWorkingday(employeeContractId, date);
            if (workingday == null) {
                workingday = new Workingday();
                workingday.setEmployeecontract(contract);
                workingday.setRefday(date);
            }
            if (form.isNotWorked()) {
                workingday.setType(Workingday.WorkingDayType.NOT_WORKED);
                workingday.setStarttimehour(0);
                workingday.setStarttimeminute(0);
                workingday.setBreakhours(0);
                workingday.setBreakminutes(0);
            } else {
                int[] start = parseTime(form.getStartTime(), 8, 0);
                int[] brk   = parseTime(form.getBreakTime(), 0, 30);
                workingday.setType(Workingday.WorkingDayType.WORKED);
                workingday.setStarttimehour(start[0]);
                workingday.setStarttimeminute(start[1]);
                workingday.setBreakhours(brk[0]);
                workingday.setBreakminutes(brk[1]);
            }
            workingdayService.upsertWorkingday(workingday);
            if ("true".equals(request.getHeader("HX-Request"))) {
                var dailyData = dailyService.buildDailyView(date, employeeContractId);
                model.addAttribute("dailyData", dailyData);
                model.addAttribute("weekStripData", dailyData.weekStrip());
                var updatedForm = new WorkingdayForm();
                updatedForm.setEmployeeContractId(employeeContractId);
                updatedForm.setDate(date);
                updatedForm.setNotWorked(dailyData.notWorked());
                updatedForm.setStartTime(dailyData.startTime());
                updatedForm.setBreakTime(dailyData.breakTime());
                model.addAttribute("workingdayForm", updatedForm);
                model.addAttribute("date", date);
                model.addAttribute("selectedContractId", employeeContractId);
                model.addAttribute("isHtmxRequest", true);
                return "dailyreport/daily :: dailyBookings";
            }
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.daily.workingday.save.success.text"));
        } catch (ErrorCodeException ex) {
            String errMsg = errorCodeViewHelper.toViewMessages(ex).stream()
                .map(Object::toString).findFirst().orElse("Error");
            if ("true".equals(request.getHeader("HX-Request"))) {
                response.setHeader("HX-Trigger", "{\"showError\":\"" + errMsg.replace("\"", "'") + "\"}");
                var dailyData = dailyService.buildDailyView(date, employeeContractId);
                model.addAttribute("dailyData", dailyData);
                model.addAttribute("weekStripData", dailyData.weekStrip());
                var updatedForm = new WorkingdayForm();
                updatedForm.setEmployeeContractId(employeeContractId);
                updatedForm.setDate(date);
                updatedForm.setNotWorked(dailyData.notWorked());
                updatedForm.setStartTime(dailyData.startTime());
                updatedForm.setBreakTime(dailyData.breakTime());
                model.addAttribute("workingdayForm", updatedForm);
                model.addAttribute("date", date);
                model.addAttribute("selectedContractId", employeeContractId);
                model.addAttribute("isHtmxRequest", true);
                return "dailyreport/daily :: dailyBookings";
            }
            redirectAttributes.addFlashAttribute("toastError", errMsg);
        }
        return "redirect:/dailyreport/daily?mode=daily&date=" + date + "&employeeContractId=" + employeeContractId;
    }

    @PostMapping("/timereport/{id}/update-inline")
    @PreAuthorize("isAuthenticated()")
    public String updateTimereportInline(
            @PathVariable long id,
            @RequestParam(required = false) String duration,
            @RequestParam(required = false) String taskdescription,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        LocalDate date = today();
        Long ecId = null;
        String htmxError = null;
        try {
            var tr = timereportService.getTimereportById(id);
            date = tr.getReferenceday();
            ecId = tr.getEmployeecontractId();
            long hours = tr.getDurationhours();
            long minutes = tr.getDurationminutes();
            if (duration != null && duration.matches("\\d{1,3}:\\d{2}")) {
                String[] parts = duration.split(":");
                hours = Long.parseLong(parts[0]);
                minutes = Long.parseLong(parts[1]);
            }
            String desc = taskdescription != null ? taskdescription : tr.getTaskdescription();
            timereportService.updateTimereport(id,
                ecId, tr.getEmployeeorderId(),
                date, desc, tr.isTraining(), hours, minutes);
        } catch (ErrorCodeException ex) {
            htmxError = errorCodeViewHelper.toViewMessages(ex).stream()
                .map(Object::toString).findFirst().orElse("Error");
        }
        if ("true".equals(request.getHeader("HX-Request")) && ecId != null) {
            if (htmxError != null) {
                response.setHeader("HX-Trigger", "{\"showError\":\"" + htmxError.replace("\"", "'") + "\"}");
            }
            var dailyData = dailyService.buildDailyView(date, ecId);
            model.addAttribute("dailyData", dailyData);
            model.addAttribute("weekStripData", dailyData.weekStrip());
            var form = new WorkingdayForm();
            form.setEmployeeContractId(ecId);
            form.setDate(date);
            form.setNotWorked(dailyData.notWorked());
            form.setStartTime(dailyData.startTime());
            form.setBreakTime(dailyData.breakTime());
            model.addAttribute("workingdayForm", form);
            model.addAttribute("date", date);
            model.addAttribute("selectedContractId", ecId);
            model.addAttribute("isHtmxRequest", true);
            return "dailyreport/daily :: dailyBookings";
        }
        return "redirect:/dailyreport/daily?mode=daily&date=" + date
            + (ecId != null ? "&employeeContractId=" + ecId : "");
    }

    @GetMapping("/new-timereport")
    public String newTimereport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Long employeeContractId,
            HttpServletRequest request) {
        request.getSession().setAttribute("currentDay", String.valueOf(date.getDayOfMonth()));
        request.getSession().setAttribute("currentMonth", date.format(DateTimeFormatter.ofPattern("MMM", java.util.Locale.ENGLISH)));
        request.getSession().setAttribute("currentYear", String.valueOf(date.getYear()));
        var contract = employeecontractService.getEmployeecontractById(employeeContractId);
        request.getSession().setAttribute("currentEmployeeContract", contract);
        request.getSession().setAttribute("currentEmployee", contract.getEmployee().getName());
        request.getSession().setAttribute("currentEmployeeId", contract.getEmployee().getId());
        return "redirect:/do/CreateDailyReport";
    }

    private static int[] parseTime(String hhmm, int defaultHour, int defaultMinute) {
        if (hhmm != null && hhmm.matches("\\d{1,2}:\\d{2}")) {
            String[] p = hhmm.split(":");
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        }
        return new int[]{defaultHour, defaultMinute};
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
