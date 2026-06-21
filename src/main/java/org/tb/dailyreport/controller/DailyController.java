package org.tb.dailyreport.controller;

import static org.tb.common.util.DateUtils.formatMonth;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
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
import org.tb.common.web.UiState;
import org.tb.employee.controller.EmployeeUiStateKeyContributor;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.service.DailyService;
import org.tb.dailyreport.service.MatrixService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import java.time.Duration;
import org.tb.favorites.domain.Favorite;
import org.tb.favorites.service.FavoriteService;
import org.tb.order.service.EmployeeorderService;

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
    private final FavoriteService favoriteService;
    private final EmployeeorderService employeeorderService;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final UiState uiState;

    @GetMapping
    public String show(
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            Model model) {

        var today = today();
        var contracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today);
        long ecId = effectiveContractId();

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
            if (ecId > 0) {
                model.addAttribute("favorites", buildFavoriteViews());
            }
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
        long employeeContractId = effectiveContractId();
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

                updatedForm.setDate(date);
                updatedForm.setNotWorked(dailyData.notWorked());
                updatedForm.setStartTime(dailyData.startTime());
                updatedForm.setBreakTime(dailyData.breakTime());
                model.addAttribute("workingdayForm", updatedForm);
                model.addAttribute("date", date);
                model.addAttribute("selectedContractId", employeeContractId);
                model.addAttribute("isHtmxRequest", true);
                model.addAttribute("isDailyMode", true);
                model.addAttribute("favorites", buildFavoriteViews());
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

                updatedForm.setDate(date);
                updatedForm.setNotWorked(dailyData.notWorked());
                updatedForm.setStartTime(dailyData.startTime());
                updatedForm.setBreakTime(dailyData.breakTime());
                model.addAttribute("workingdayForm", updatedForm);
                model.addAttribute("date", date);
                model.addAttribute("selectedContractId", employeeContractId);
                model.addAttribute("isHtmxRequest", true);
                model.addAttribute("isDailyMode", true);
                model.addAttribute("favorites", buildFavoriteViews());
                return "dailyreport/daily :: dailyBookings";
            }
            redirectAttributes.addFlashAttribute("toastError", errMsg);
        }
        return "redirect:/dailyreport/daily?mode=daily&date=" + date;
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
            String currentUrl = request.getHeader("HX-Current-URL");
            boolean isListMode = currentUrl != null && currentUrl.contains("mode=list");
            if (isListMode) {
                var tr = timereportService.getTimereportById(id);
                model.addAttribute("singleTr", tr);
                model.addAttribute("singleTrEditable", dailyService.isTimereportEditable(tr, ecId));
                model.addAttribute("singleTrDate", date);
                model.addAttribute("singleTrYearMonth", YearMonth.from(date));
                return "dailyreport/daily-list-card :: listTimereportCard";
            }
            var dailyData = dailyService.buildDailyView(date, ecId);
            model.addAttribute("dailyData", dailyData);
            model.addAttribute("weekStripData", dailyData.weekStrip());
            var form = new WorkingdayForm();
            form.setDate(date);
            form.setNotWorked(dailyData.notWorked());
            form.setStartTime(dailyData.startTime());
            form.setBreakTime(dailyData.breakTime());
            model.addAttribute("workingdayForm", form);
            model.addAttribute("date", date);
            model.addAttribute("selectedContractId", ecId);
            model.addAttribute("isHtmxRequest", true);
            model.addAttribute("isDailyMode", true);
            model.addAttribute("favorites", buildFavoriteViews());
            return "dailyreport/daily :: dailyBookings";
        }
        return "redirect:/dailyreport/daily?mode=daily&date=" + date;
    }

    @PostMapping("/apply-favourite")
    @PreAuthorize("isAuthenticated()")
    public String applyFavourite(
            @RequestParam Long favoriteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        long ecId = effectiveContractId();
        try {
            var fav = favoriteService.getFavorite(favoriteId).orElseThrow();
            timereportService.createTimereports(ecId, fav.getEmployeeorderId(), date,
                fav.getComment(), false, fav.getHours(), fav.getMinutes(), 1);
        } catch (ErrorCodeException ex) {
            String err = errorCodeViewHelper.toViewMessages(ex).stream()
                .map(Object::toString).findFirst().orElse("Error");
            response.setHeader("HX-Trigger", "{\"showError\":\"" + err.replace("\"", "'") + "\"}");
        }
        var dailyData = dailyService.buildDailyView(date, ecId);
        var wdForm = new WorkingdayForm();
        wdForm.setDate(date);
        wdForm.setNotWorked(dailyData.notWorked());
        wdForm.setStartTime(dailyData.startTime());
        wdForm.setBreakTime(dailyData.breakTime());
        model.addAttribute("dailyData", dailyData);
        model.addAttribute("weekStripData", dailyData.weekStrip());
        model.addAttribute("workingdayForm", wdForm);
        model.addAttribute("date", date);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("isHtmxRequest", true);
        model.addAttribute("isDailyMode", true);
        model.addAttribute("favorites", buildFavoriteViews());
        model.addAttribute("oobFavourites", true);
        return "dailyreport/daily :: dailyBookings";
    }

    @PostMapping("/delete-favourite")
    @PreAuthorize("isAuthenticated()")
    public String deleteFavourite(
            @RequestParam Long favoriteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        long ecId = effectiveContractId();
        try {
            favoriteService.deleteFavorite(favoriteId);
        } catch (Exception ex) {
            // favourite already gone or access denied — ignore
        }
        var dailyData = dailyService.buildDailyView(date, ecId);
        model.addAttribute("dailyData", dailyData);
        model.addAttribute("weekStripData", dailyData.weekStrip());
        var form = new WorkingdayForm();
        form.setDate(date);
        form.setNotWorked(dailyData.notWorked());
        form.setStartTime(dailyData.startTime());
        form.setBreakTime(dailyData.breakTime());
        model.addAttribute("workingdayForm", form);
        model.addAttribute("date", date);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("isHtmxRequest", true);
        model.addAttribute("isDailyMode", true);
        model.addAttribute("favorites", buildFavoriteViews());
        model.addAttribute("oobFavourites", true);
        return "dailyreport/daily :: dailyBookings";
    }

    private List<FavoriteView> buildFavoriteViews() {
        long empId = employeeService.getLoginEmployee().getId();
        return favoriteService.getFavorites(empId).stream()
            .map(this::buildFavoriteView)
            .filter(Objects::nonNull)
            .toList();
    }

    private FavoriteView buildFavoriteView(Favorite f) {
        var eo = employeeorderService.getEmployeeorderById(f.getEmployeeorderId());
        if (eo == null) return null;
        String label = eo.getSuborder().getCompleteOrderSignAndDescription();
        Duration duration = Duration.ofHours(f.getHours()).plusMinutes(f.getMinutes());
        return new FavoriteView(f.getId(), label, f.getComment(), duration);
    }

    private long effectiveContractId() {
        Long fromUiState = uiState.getLongValue(EmployeeUiStateKeyContributor.SELECTED_CONTRACT);
        if (fromUiState != null && fromUiState > 0) return fromUiState;
        var loginEmployee = employeeService.getLoginEmployee();
        return employeecontractService.getCurrentContract(loginEmployee.getId())
            .map(c -> c.getId())
            .orElse(-1L);
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
            return "redirect:/dailyreport/daily?mode=list&month=" + month + "&year=" + year;
        }
        return "redirect:/dailyreport/daily?mode=daily&date=" + date;
    }

    @PostMapping("/fill-not-worked")
    @PreAuthorize("isAuthenticated()")
    public String fillNotWorked(
            @RequestParam Integer month,
            @RequestParam Integer year,
            RedirectAttributes redirectAttributes) {
        long ecId = effectiveContractId();
        try {
            matrixService.fillNotWorked(YearMonth.of(year, month), ecId);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.matrix.fillnotworked.success.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream()
                    .map(Object::toString).findFirst().orElse("Error"));
        }
        return "redirect:/dailyreport/daily?mode=list&month=" + month + "&year=" + year;
    }
}
