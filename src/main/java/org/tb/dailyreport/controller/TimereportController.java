package org.tb.dailyreport.controller;

import static java.math.BigDecimal.valueOf;
import static org.tb.common.util.DateUtils.today;

import java.time.LocalDate;
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
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.web.UiState;
import org.tb.employee.controller.EmployeeUiStateKeyContributor;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.favorites.domain.Favorite;
import org.tb.favorites.service.FavoriteService;
import org.tb.order.domain.Customerorder;
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
    private final AuthorizedUser authorizedUser;
    private final AuthorizedEmployee authorizedEmployee;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final UiState uiState;

    @GetMapping("/new")
    public String createForm(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {

        LocalDate effectiveDate = date != null ? date : today();
        long ecId = effectiveContractId();

        var orders = customerorderService.getCustomerordersWithValidEmployeeOrders(ecId, effectiveDate);
        Long firstOrderId = orders.isEmpty() ? null : orders.get(0).getId();

        List<SuborderOption> suborders = List.of();
        Long firstSuborderId = null;
        if (firstOrderId != null) {
            suborders = suborderOptions(ecId, firstOrderId, effectiveDate);
            firstSuborderId = suborders.isEmpty() ? null : suborders.get(0).id();
        }

        var form = new TimereportForm();

        form.setReferenceday(effectiveDate);
        form.setOrderId(firstOrderId);
        form.setSuborderId(firstSuborderId);

        populateModel(model, form, orders, suborders, ecId, effectiveDate, false);
        return "dailyreport/timereport-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
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

        var orders = customerorderService.getCustomerordersWithValidEmployeeOrders(ecId, date);
        var suborders = suborderOptions(ecId, tr.getCustomerorderId(), date);

        populateModel(model, form, orders, suborders, ecId, date, true);
        return "dailyreport/timereport-form";
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public String create(
            @ModelAttribute TimereportForm form,
            RedirectAttributes redirectAttributes,
            Model model) {
        return saveTimereport(form, false, redirectAttributes, model);
    }

    @PostMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public String update(
            @PathVariable Long id,
            @ModelAttribute TimereportForm form,
            RedirectAttributes redirectAttributes,
            Model model) {
        form.setId(id);
        return saveTimereport(form, true, redirectAttributes, model);
    }

    @PostMapping("/refresh-orders")
    @PreAuthorize("isAuthenticated()")
    public String refreshOrders(@ModelAttribute TimereportForm form, Model model) {
        long ecId = effectiveContractId();
        LocalDate date = form.getReferenceday();

        List<Customerorder> orders = List.of();
        List<SuborderOption> suborders = List.of();
        if (ecId > 0 && date != null) {
            orders = customerorderService.getCustomerordersWithValidEmployeeOrders(ecId, date);
            Long requestedOrderId = form.getOrderId();
            boolean orderStillValid = orders.stream().anyMatch(o -> o.getId().equals(requestedOrderId));
            Long firstOrderId;
            if (!orderStillValid) {
                firstOrderId = orders.isEmpty() ? null : orders.get(0).getId();
                form.setOrderId(firstOrderId);
                form.setSuborderId(null);
            } else {
                firstOrderId = requestedOrderId;
            }
            if (firstOrderId != null) {
                suborders = suborderOptions(ecId, firstOrderId, date);
                if (suborders.stream().noneMatch(s -> s.id().equals(form.getSuborderId()))) {
                    form.setSuborderId(suborders.isEmpty() ? null : suborders.get(0).id());
                }
            }
        }

        boolean commentNecessaryOrders = suborders.stream()
            .filter(s -> s.id().equals(form.getSuborderId()))
            .findFirst().map(SuborderOption::commentNecessary).orElse(false);
        model.addAttribute("timereportForm", form);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("orders", orders);
        model.addAttribute("suborders", suborders);
        model.addAttribute("commentNecessary", commentNecessaryOrders);
        model.addAttribute("recentComments", loadRecentComments(form));
        if (ecId > 0 && date != null) {
            model.addAttribute("todaysBookings",
                timereportService.getTimereportsByDateAndEmployeeContractId(ecId, date));
        } else {
            model.addAttribute("todaysBookings", List.of());
        }
        if (authorizedUser.isPeopleLead()) {
            model.addAttribute("employeecontracts",
                employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(
                    date != null ? date : today()));
        }
        model.addAttribute("oobSidebar", true);
        return "dailyreport/timereport-form :: ordersRefreshCompositeFragment";
    }

    @PostMapping("/refresh-suborders")
    @PreAuthorize("isAuthenticated()")
    public String refreshSuborders(@ModelAttribute TimereportForm form, Model model) {
        long ecId = effectiveContractId();
        LocalDate date = form.getReferenceday();

        List<SuborderOption> suborders = List.of();
        if (form.getOrderId() != null && ecId > 0 && date != null) {
            suborders = suborderOptions(ecId, form.getOrderId(), date);
        }
        // reset suborderId if no longer valid after order change
        if (suborders.stream().noneMatch(s -> s.id().equals(form.getSuborderId()))) {
            form.setSuborderId(suborders.isEmpty() ? null : suborders.get(0).id());
        }
        boolean commentNecessarySuborders = suborders.stream()
            .filter(s -> s.id().equals(form.getSuborderId()))
            .findFirst().map(SuborderOption::commentNecessary).orElse(false);
        model.addAttribute("timereportForm", form);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("suborders", suborders);
        model.addAttribute("commentNecessary", commentNecessarySuborders);
        model.addAttribute("recentComments", loadRecentComments(form));
        if (ecId > 0 && date != null) {
            model.addAttribute("todaysBookings",
                timereportService.getTimereportsByDateAndEmployeeContractId(ecId, date));
        } else {
            model.addAttribute("todaysBookings", List.of());
        }
        if (authorizedUser.isPeopleLead()) {
            model.addAttribute("employeecontracts",
                employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(
                    date != null ? date : today()));
        }
        model.addAttribute("oobSidebar", true);
        return "dailyreport/timereport-form :: suborderRefreshCompositeFragment";
    }

    @PostMapping("/refresh-sidebar")
    @PreAuthorize("isAuthenticated()")
    public String refreshSidebar(@ModelAttribute TimereportForm form, Model model) {
        long ecId = effectiveContractId();
        model.addAttribute("timereportForm", form);
        model.addAttribute("selectedContractId", ecId);
        if (ecId > 0 && form.getReferenceday() != null) {
            model.addAttribute("todaysBookings",
                timereportService.getTimereportsByDateAndEmployeeContractId(ecId, form.getReferenceday()));
        } else {
            model.addAttribute("todaysBookings", List.of());
        }
        model.addAttribute("recentComments", loadRecentComments(form));
        if (authorizedUser.isPeopleLead()) {
            model.addAttribute("employeecontracts",
                employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(
                    form.getReferenceday() != null ? form.getReferenceday() : today()));
        }
        return "dailyreport/timereport-form :: sidebarFragment";
    }

    // ---- private helpers ----

    private String saveTimereport(TimereportForm form, boolean isEdit,
            RedirectAttributes redirectAttributes, Model model) {

        long ecId = effectiveContractId();
        LocalDate date = form.getReferenceday();

        long durationHours;
        long durationMinutes;
        if ("beginEnd".equals(form.getDurationMode())
                && form.getBeginTime() != null && form.getEndTime() != null) {
            int[] begin = parseTime(form.getBeginTime());
            int[] end = parseTime(form.getEndTime());
            long totalMinutes = (end[0] * 60L + end[1]) - (begin[0] * 60L + begin[1]);
            if (totalMinutes <= 0) {
                return reRenderFormWithError(model, form, ecId, date, isEdit,
                        errorCodeViewHelper.toViewMessage("main.timereport.form.validation.duration.positive"));
            }
            if (totalMinutes > 1440) {
                return reRenderFormWithError(model, form, ecId, date, isEdit,
                        errorCodeViewHelper.toViewMessage("main.timereport.form.validation.duration.range"));

            }
            durationHours = totalMinutes / 60;
            durationMinutes = totalMinutes % 60;
        } else {
            int[] parts = parseTime(form.getDurationTime());
            long totalMinutes = parts[0] * 60L + parts[1];
            if (totalMinutes <= 0 || totalMinutes > 1440) {
                return reRenderFormWithError(model, form, ecId, date, isEdit,
                        errorCodeViewHelper.toViewMessage("main.timereport.form.validation.duration.range"));
            }
            durationHours = parts[0];
            durationMinutes = parts[1];
        }

        if (form.getSuborderId() == null) {
            return reRenderFormWithError(model, form, ecId, date, isEdit,
                    errorCodeViewHelper.toViewMessage("main.timereport.form.validation.suborder.required"));
        }

        try {
            // seed workingday start time for all serial days when not yet set
            if (!isEdit) {
                boolean useBegin = "beginEnd".equals(form.getDurationMode()) && form.getBeginTime() != null;
                int[] begin = useBegin ? parseTime(form.getBeginTime()) : new int[]{8, 0};
                var serialDates = timereportService.getWorkableSerialDates(date, form.getNumberOfSerialDays());
                for (LocalDate serialDate : serialDates) {
                    seedWorkingday(ecId, serialDate, begin);
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
            var orders = customerorderService.getCustomerordersWithValidEmployeeOrders(ecId, date);
            var suborders = form.getOrderId() != null
                ? suborderOptions(ecId, form.getOrderId(), date)
                : List.<SuborderOption>of();
            populateModel(model, form, orders, suborders, ecId, date, isEdit);
            model.addAttribute("errors", errorCodeViewHelper.toViewMessages(ex));
            return "dailyreport/timereport-form";
        }
    }

    private String reRenderFormWithError(Model model, TimereportForm form, long ecId, LocalDate date,
            boolean isEdit, ErrorCodeViewHelper.ViewMessage errorMessage) {
        var orders = customerorderService.getCustomerordersWithValidEmployeeOrders(ecId, date);
        var suborders = form.getOrderId() != null
            ? suborderOptions(ecId, form.getOrderId(), date)
            : List.<SuborderOption>of();
        populateModel(model, form, orders, suborders, ecId, date, isEdit);
        model.addAttribute("errors", List.of(errorMessage));
        return "dailyreport/timereport-form";
    }

    private void populateModel(Model model, TimereportForm form, List<Customerorder> orders,
            List<SuborderOption> suborders, long ecId, LocalDate date, boolean isEdit) {
        boolean commentNecessary = suborders.stream()
            .filter(s -> s.id().equals(form.getSuborderId()))
            .findFirst().map(SuborderOption::commentNecessary).orElse(false);
        model.addAttribute("timereportForm", form);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("orders", orders);
        model.addAttribute("suborders", suborders);
        model.addAttribute("commentNecessary", commentNecessary);
        model.addAttribute("isEdit", isEdit);
        var todaysBookings = timereportService.getTimereportsByDateAndEmployeeContractId(ecId, date);
        model.addAttribute("todaysBookings", todaysBookings);
        model.addAttribute("recentComments", loadRecentComments(form));
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
        model.addAttribute("section", "dailyreport");
        model.addAttribute("subSection", "timereports");
        model.addAttribute("sectionTitle",
            messages.getMessage("main.general.mainmenu.timereports.text"));
        model.addAttribute("pageTitle",
            messages.getMessage(isEdit
                ? "main.timereport.form.title.edit"
                : "main.timereport.form.title.create"));
        if (authorizedUser.isPeopleLead()) {
            model.addAttribute("employeecontracts",
                employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(date));
        }
    }

    private List<String> loadRecentComments(TimereportForm form) {
        if (form.getSuborderId() != null) {
            long ecId = effectiveContractId();
            if (ecId > 0) {
                return timereportService.getRecentComments(ecId, form.getSuborderId());
            }
        }
        return List.of();
    }

    private void seedWorkingday(long ecId, LocalDate date, int[] begin) {
        var workingday = workingdayService.getWorkingday(ecId, date);
        if (workingday == null) {
            workingday = new Workingday();
            workingday.setEmployeecontract(employeecontractService.getEmployeecontractById(ecId));
            workingday.setRefday(date);
            workingday.setBreakhours(0);
            workingday.setBreakminutes(0);
            workingday.setStarttimehour(begin[0]);
            workingday.setStarttimeminute(begin[1]);
        } else {
            if(workingday.getStarttimehour() == 0) workingday.setStarttimehour(begin[0]);
            if(workingday.getStarttimeminute() == 0) workingday.setStarttimeminute(begin[1]);
        }
        workingday.setType(Workingday.WorkingDayType.WORKED);
        workingdayService.upsertWorkingday(workingday);
    }

    private List<SuborderOption> suborderOptions(long ecId, long orderId, LocalDate date) {
        return suborderService.getSuborderSummaries(ecId, orderId, date)
            .stream()
            .map(s -> {
                var desc  = s.shortdescription();
                var label = (desc != null && !desc.isBlank())
                    ? s.completeOrderSign() + " — " + desc
                    : s.completeOrderSign();
                return new SuborderOption(s.id(), label, s.commentNecessary());
            })
            .toList();
    }

    private long effectiveContractId() {
        Long fromCookie = uiState.getLong(EmployeeUiStateKeyContributor.SELECTED_CONTRACT);
        if (fromCookie != null && fromCookie > 0) return fromCookie;
        var loginEmployee = employeeService.getLoginEmployee();
        return employeecontractService.getCurrentContract(loginEmployee.getId())
            .map(c -> c.getId())
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
