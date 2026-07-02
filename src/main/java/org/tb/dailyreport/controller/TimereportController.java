package org.tb.dailyreport.controller;

import static java.math.BigDecimal.valueOf;
import static org.tb.common.util.DateUtils.today;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;
import org.tb.auth.domain.Authorized;
import org.tb.dailyreport.preferences.DailyPreferenceService;
import org.tb.dailyreport.preferences.TimereportPreferenceService;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.favorites.domain.Favorite;
import org.tb.favorites.service.FavoriteService;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;

import static org.tb.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_NOT_FOUND;

@Controller
@RequestMapping("/dailyreport/timereports")
@RequiredArgsConstructor
@Authorized
public class TimereportController {

    private final TimereportService timereportService;
    private final EmployeecontractService employeecontractService;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final EmployeeorderService employeeorderService;
    private final WorkingdayService workingdayService;
    private final FavoriteService favoriteService;
    private final EmployeeService employeeService;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final DailyPreferenceService dailyPreferenceService;
    private final TimereportPreferenceService timereportPreferenceService;

    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) Long employeeContractId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {

        LocalDate effectiveDate = date != null ? date : today();
        long ecId = effectiveContractId(employeeContractId);

        var suborders = suborderOptions(ecId, effectiveDate);
        Long favoriteId = timereportPreferenceService.getForCurrentUser().favoriteSuborderId();
        Long defaultSuborderId = suborders.stream()
                .map(SuborderOption::id)
                .filter(id -> id.equals(favoriteId))
                .findFirst()
                .orElse(suborders.isEmpty() ? null : suborders.get(0).id());

        var form = new TimereportForm();
        form.setReferenceday(effectiveDate);
        form.setSuborderId(defaultSuborderId);

        populateModel(employeeContractId, model, form, suborders, ecId, effectiveDate, false);
        return "dailyreport/timereport-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, @RequestParam(required = false) Long employeeContractId, Model model) {
        var tr = timereportService.getTimereportById(id);
        if (tr == null) {
            return "redirect:/dailyreport/daily";
        }

        long ecId = tr.getEmployeecontractId();
        LocalDate date = tr.getReferenceday();

        var form = new TimereportForm();
        form.setId(id);

        form.setReferenceday(date);
        form.setOrderId(tr.getCustomerorderId());
        form.setSuborderId(tr.getSuborderId());
        form.setDurationTime(String.format("%02d:%02d",
                valueOf(tr.getDurationhours()).intValueExact(),
                valueOf(tr.getDurationminutes()).intValueExact()));
        form.setComment(tr.getTaskdescription() != null ? tr.getTaskdescription() : "");
        form.setTraining(tr.isTraining());

        var suborders = suborderOptions(ecId, date);

        populateModel(employeeContractId, model, form, suborders, ecId, date, true);
        return "dailyreport/timereport-form";
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public String create(
            @RequestParam(required = false) Long employeeContractId,
            @ModelAttribute TimereportForm form,
            RedirectAttributes redirectAttributes,
            Model model) {
        return saveTimereport(employeeContractId, form, false, redirectAttributes, model);
    }

    @PostMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public String update(
            @PathVariable Long id,
            @RequestParam(required = false) Long employeeContractId,
            @ModelAttribute TimereportForm form,
            RedirectAttributes redirectAttributes,
            Model model) {
        form.setId(id);
        return saveTimereport(employeeContractId, form, true, redirectAttributes, model);
    }

    @PostMapping("/refresh-orders")
    @PreAuthorize("isAuthenticated()")
    public String refreshOrders(@RequestParam(required = false) Long employeeContractId, @ModelAttribute TimereportForm form, Model model) {
        long ecId = effectiveContractId(employeeContractId);
        LocalDate date = form.getReferenceday();

        List<SuborderOption> suborders = List.of();
        if (ecId > 0 && date != null) {
            suborders = suborderOptions(ecId, date);
            if (suborders.stream().noneMatch(s -> s.id().equals(form.getSuborderId()))) {
                form.setSuborderId(suborders.isEmpty() ? null : suborders.get(0).id());
            }
        }

        boolean commentNecessary = suborders.stream()
            .filter(s -> s.id().equals(form.getSuborderId()))
            .findFirst().map(SuborderOption::commentNecessary).orElse(false);
        model.addAttribute("timereportForm", form);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("suborders", suborders);
        model.addAttribute("commentNecessary", commentNecessary);
        model.addAttribute("recentComments", loadRecentComments(employeeContractId, form));
        if (ecId > 0 && date != null) {
            model.addAttribute("todaysBookings",
                timereportService.getTimereportsByDateAndEmployeeContractId(ecId, date));
        } else {
            model.addAttribute("todaysBookings", List.of());
        }
        model.addAttribute("favoriteSuborderId", timereportPreferenceService.getForCurrentUser().favoriteSuborderId());
        model.addAttribute("oobSidebar", true);
        return "dailyreport/timereport-form :: ordersRefreshCompositeFragment";
    }

    @PostMapping("/refresh-sidebar")
    @PreAuthorize("isAuthenticated()")
    public String refreshSidebar(@RequestParam(required = false) Long employeeContractId, @ModelAttribute TimereportForm form, Model model) {
        long ecId = effectiveContractId(employeeContractId);
        model.addAttribute("timereportForm", form);
        model.addAttribute("selectedContractId", ecId);
        if (ecId > 0 && form.getReferenceday() != null) {
            model.addAttribute("todaysBookings",
                timereportService.getTimereportsByDateAndEmployeeContractId(ecId, form.getReferenceday()));
        } else {
            model.addAttribute("todaysBookings", List.of());
        }
        model.addAttribute("recentComments", loadRecentComments(employeeContractId, form));
        return "dailyreport/timereport-form :: sidebarFragment";
    }

    @PostMapping("/preferences/favorite-suborder")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<Void> setFavoriteSuborder(@RequestParam(required = false) Long suborderId) {
        timereportPreferenceService.toggleFavoriteSuborder(suborderId);
        return ResponseEntity.noContent().build();
    }

    // ---- private helpers ----

    private String saveTimereport(Long employeeContractId, TimereportForm form, boolean isEdit,
            RedirectAttributes redirectAttributes, Model model) {

        long ecId = effectiveContractId(employeeContractId);
        LocalDate date = form.getReferenceday();

        long durationHours;
        long durationMinutes;
        if ("beginEnd".equals(form.getDurationMode())
                && form.getBeginTime() != null && form.getEndTime() != null) {
            int[] begin = parseTime(form.getBeginTime());
            int[] end = parseTime(form.getEndTime());
            long totalMinutes = (end[0] * 60L + end[1]) - (begin[0] * 60L + begin[1]);
            if (totalMinutes <= 0) {
                return reRenderFormWithError(employeeContractId, model, form, ecId, date, isEdit,
                        errorCodeViewHelper.toViewMessage("main.timereport.form.validation.duration.positive"));
            }
            if (totalMinutes > 1440) {
                return reRenderFormWithError(employeeContractId, model, form, ecId, date, isEdit,
                        errorCodeViewHelper.toViewMessage("main.timereport.form.validation.duration.range"));

            }
            durationHours = totalMinutes / 60;
            durationMinutes = totalMinutes % 60;
        } else {
            int[] parts = parseTime(form.getDurationTime());
            long totalMinutes = parts[0] * 60L + parts[1];
            if (totalMinutes <= 0 || totalMinutes > 1440) {
                return reRenderFormWithError(employeeContractId, model, form, ecId, date, isEdit,
                        errorCodeViewHelper.toViewMessage("main.timereport.form.validation.duration.range"));
            }
            durationHours = parts[0];
            durationMinutes = parts[1];
        }

        if (form.getSuborderId() == null) {
            return reRenderFormWithError(employeeContractId, model, form, ecId, date, isEdit,
                    errorCodeViewHelper.toViewMessage("main.timereport.form.validation.suborder.required"));
        }

        try {
            // seed workingday start time for all serial days when not yet set
            if (!isEdit) {
                boolean useBegin = "beginEnd".equals(form.getDurationMode()) && form.getBeginTime() != null;
                LocalTime beginTime;
                if (useBegin) {
                    int[] begin = parseTime(form.getBeginTime());
                    beginTime = LocalTime.of(begin[0], begin[1]);
                } else {
                    beginTime = dailyPreferenceService.getForEmployeeContractId(ecId).workDayStart();
                }
                var serialDates = timereportService.getWorkableSerialDates(date, form.getNumberOfSerialDays());
                for (LocalDate serialDate : serialDates) {
                    seedWorkingday(ecId, serialDate, beginTime);
                }
            }

            var eo = employeeorderService.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(
                    ecId, form.getSuborderId(), date);
            if (eo == null) {
                throw new InvalidDataException(TR_EMPLOYEE_ORDER_NOT_FOUND);
            }
            long employeeOrderId = eo.getId();

            if (isEdit) {
                timereportService.updateTimereport(form.getId(), ecId, employeeOrderId, date,
                        form.getComment(), form.isTraining(), durationHours, durationMinutes);
            } else {
                timereportService.createTimereports(ecId, employeeOrderId, date,
                        form.getComment(), form.isTraining(), durationHours, durationMinutes,
                        form.getNumberOfSerialDays());
            }

            if (form.isSaveAsFavorite()) {
                var fav = Favorite.builder()
                    .employeeorderId(employeeOrderId)
                    .hours(valueOf(durationHours).intValueExact())
                    .minutes(valueOf(durationMinutes).intValueExact())
                    .comment(form.getComment())
                    .build();
                favoriteService.addFavorite(fav);
            }

            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage(isEdit
                    ? "main.timereport.update.success.text"
                    : "main.timereport.create.success.text"));
            return "redirect:/dailyreport/daily?mode=daily&date=" + date;

        } catch (ErrorCodeException ex) {
            var suborders = suborderOptions(ecId, date);
            populateModel(employeeContractId, model, form, suborders, ecId, date, isEdit);
            model.addAttribute("errors", errorCodeViewHelper.toViewMessages(ex));
            return "dailyreport/timereport-form";
        }
    }

    private String reRenderFormWithError(Long employeeContractId, Model model, TimereportForm form, long ecId, LocalDate date,
            boolean isEdit, ErrorCodeViewHelper.ViewMessage errorMessage) {
        var suborders = suborderOptions(ecId, date);
        populateModel(employeeContractId, model, form, suborders, ecId, date, isEdit);
        model.addAttribute("errors", List.of(errorMessage));
        return "dailyreport/timereport-form";
    }

    private void populateModel(Long employeeContractId, Model model, TimereportForm form,
            List<SuborderOption> suborders, long ecId, LocalDate date, boolean isEdit) {
        boolean commentNecessary = suborders.stream()
            .filter(s -> s.id().equals(form.getSuborderId()))
            .findFirst().map(SuborderOption::commentNecessary).orElse(false);
        model.addAttribute("timereportForm", form);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("suborders", suborders);
        model.addAttribute("commentNecessary", commentNecessary);
        model.addAttribute("isEdit", isEdit);
        var todaysBookings = timereportService.getTimereportsByDateAndEmployeeContractId(ecId, date);
        model.addAttribute("todaysBookings", todaysBookings);
        model.addAttribute("recentComments", loadRecentComments(employeeContractId, form));
        if (!isEdit && date != null && date.equals(today())) {
            var workingday = workingdayService.getWorkingday(ecId, date);
            if (workingday != null && (workingday.getStarttimehour() > 0 || workingday.getStarttimeminute() > 0)) {
                long bookedMinutes = todaysBookings.stream()
                    .mapToLong(tr -> tr.getDuration().toMinutes())
                    .sum();
                long startMinutes = workingday.getStarttimehour() * 60L + workingday.getStarttimeminute()
                    + workingday.getBreakhours() * 60L + workingday.getBreakminutes()
                    + bookedMinutes;
                model.addAttribute("liveBookingStartMinutes", startMinutes);
            }
        }
        if (ecId > 0) {
            var ec = employeecontractService.getEmployeecontractById(ecId);
            if (ec != null) {
                model.addAttribute("selectedEmployeeName", ec.getEmployee().getName() + " | " + ec.getEmployee().getSign()
                    + "  (" + ec.getTimeString() + (ec.getOpenEnd() ? " ∞" : "") + ")");
            }
        }
        model.addAttribute("favoriteSuborderId", timereportPreferenceService.getForCurrentUser().favoriteSuborderId());
        model.addAttribute("section", "dailyreport");
        model.addAttribute("subSection", "timereports");
        model.addAttribute("sectionTitle",
            messages.getMessage("main.general.mainmenu.timereports.text"));
        model.addAttribute("pageTitle",
            messages.getMessage(isEdit
                ? "main.timereport.form.title.edit"
                : "main.timereport.form.title.create"));
    }

    private List<String> loadRecentComments(Long employeeContractId, TimereportForm form) {
        if (form.getSuborderId() != null) {
            long ecId = effectiveContractId(employeeContractId);
            if (ecId > 0) {
                return timereportService.getRecentComments(ecId, form.getSuborderId());
            }
        }
        return List.of();
    }

    private void seedWorkingday(long ecId, LocalDate date, LocalTime beginTime) {
        workingdayService.seedWorkingday(ecId, date, beginTime.getHour(), beginTime.getMinute());
    }

    private List<SuborderOption> suborderOptions(long ecId, LocalDate date) {
        return customerorderService.getCustomerordersWithValidEmployeeOrders(ecId, date)
            .stream()
            .flatMap(order -> suborderService.getSuborderSummaries(ecId, order.getId(), date).stream()
                .map(s -> {
                    var desc = s.shortdescription();
                    var label = (desc != null && !desc.isBlank())
                        ? s.completeOrderSign() + " · " + desc
                        : s.completeOrderSign();
                    var subtext = order.getSign() + " · " + order.getShortdescription()
                        + " · " + order.getCustomer().getShortname();
                    return new SuborderOption(s.id(), label, subtext, s.commentNecessary());
                }))
            .toList();
    }

    private long effectiveContractId(Long employeeContractId) {
        if (employeeContractId != null && employeeContractId > 0) {
            return employeeContractId;
        }
        var loginEmployee = employeeService.getLoginEmployee();
        return employeecontractService.getCurrentContract(loginEmployee.getId())
                .map(Employeecontract::getId)
                .orElse(-1L);
    }

    private static int[] parseTime(String hhmm) {
        if (hhmm != null && hhmm.matches("\\d{1,2}:\\d{2}")) {
            String[] p = hhmm.split(":");
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        }
        return new int[]{0, 0};
    }
}
