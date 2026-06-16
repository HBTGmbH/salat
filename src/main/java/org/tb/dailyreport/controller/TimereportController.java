package org.tb.dailyreport.controller;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletResponse;
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
import org.tb.order.service.SuborderOption;
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

    @GetMapping("/new")
    public String createForm(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long employeeContractId,
            Model model) {

        LocalDate effectiveDate = date != null ? date : today();
        long ecId = resolveContractId(employeeContractId, effectiveDate);

        var orders = customerorderService.getCustomerordersWithValidEmployeeOrders(ecId, effectiveDate);
        Long firstOrderId = orders.isEmpty() ? null : orders.get(0).getId();

        List<SuborderOption> suborders = List.of();
        Long firstSuborderId = null;
        if (firstOrderId != null) {
            suborders = suborderService.getSuborderOptionsForForm(ecId, firstOrderId, effectiveDate);
            firstSuborderId = suborders.isEmpty() ? null : suborders.get(0).id();
        }

        var form = new TimereportForm();
        form.setEmployeeContractId(ecId);
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
        form.setEmployeeContractId(ecId);
        form.setReferenceday(date);
        form.setOrderId(tr.getCustomerorderId());
        form.setSuborderId(tr.getSuborderId());
        form.setDurationHours((int) tr.getDurationhours());
        form.setDurationMinutes((int) tr.getDurationminutes());
        form.setComment(tr.getTaskdescription() != null ? tr.getTaskdescription() : "");
        form.setTraining(tr.isTraining());

        var orders = customerorderService.getCustomerordersWithValidEmployeeOrders(ecId, date);
        var suborders = suborderService.getSuborderOptionsForForm(ecId, tr.getCustomerorderId(), date);

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
    public String refreshOrders(@ModelAttribute TimereportForm form, Model model,
            HttpServletResponse response) {
        long ecId = form.getEmployeeContractId() != null ? form.getEmployeeContractId() : -1L;
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
                suborders = suborderService.getSuborderOptionsForForm(ecId, firstOrderId, date);
                if (suborders.stream().noneMatch(s -> s.id().equals(form.getSuborderId()))) {
                    form.setSuborderId(suborders.isEmpty() ? null : suborders.get(0).id());
                }
            }
        }

        model.addAttribute("timereportForm", form);
        model.addAttribute("orders", orders);
        model.addAttribute("suborders", suborders);
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
        response.setHeader("HX-Trigger", "ordersRefreshed");
        return "dailyreport/timereport-form :: ordersAndSubordersFragment";
    }

    @PostMapping("/refresh-suborders")
    @PreAuthorize("isAuthenticated()")
    public String refreshSuborders(@ModelAttribute TimereportForm form, Model model) {
        List<SuborderOption> suborders = List.of();
        if (form.getOrderId() != null && form.getEmployeeContractId() != null && form.getReferenceday() != null) {
            suborders = suborderService.getSuborderOptionsForForm(
                form.getEmployeeContractId(), form.getOrderId(), form.getReferenceday());
        }
        // reset suborderId if no longer valid after order change
        if (suborders.stream().noneMatch(s -> s.id().equals(form.getSuborderId()))) {
            form.setSuborderId(suborders.isEmpty() ? null : suborders.get(0).id());
        }
        model.addAttribute("timereportForm", form);
        model.addAttribute("suborders", suborders);
        model.addAttribute("recentComments", loadRecentComments(form));
        return "dailyreport/timereport-form :: suborderFragment";
    }

    @PostMapping("/refresh-sidebar")
    @PreAuthorize("isAuthenticated()")
    public String refreshSidebar(@ModelAttribute TimereportForm form, Model model) {
        long ecId = form.getEmployeeContractId() != null ? form.getEmployeeContractId() : -1L;
        model.addAttribute("timereportForm", form);
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

        long ecId = form.getEmployeeContractId() != null
            ? form.getEmployeeContractId()
            : resolveContractId(null, form.getReferenceday());
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
                    messages.getMessage("main.timereport.form.validation.duration.positive"));
            }
            durationHours = totalMinutes / 60;
            durationMinutes = totalMinutes % 60;
        } else {
            durationHours = form.getDurationHours();
            durationMinutes = form.getDurationMinutes();
        }

        if (form.getSuborderId() == null) {
            return reRenderFormWithError(model, form, ecId, date, isEdit,
                messages.getMessage("main.timereport.form.validation.suborder.required"));
        }

        try {
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

            // update workingday start time from begin/end mode when start is not yet set
            if ("beginEnd".equals(form.getDurationMode()) && form.getBeginTime() != null && !isEdit) {
                int[] begin = parseTime(form.getBeginTime());
                var workingday = workingdayService.getWorkingday(ecId, date);
                if (workingday == null || workingday.getStartOfWorkingDay() == null) {
                    if (workingday == null) {
                        workingday = new Workingday();
                        workingday.setEmployeecontract(employeecontractService.getEmployeecontractById(ecId));
                        workingday.setRefday(date);
                        workingday.setType(Workingday.WorkingDayType.WORKED);
                        workingday.setBreakhours(0);
                        workingday.setBreakminutes(0);
                    }
                    workingday.setStarttimehour(begin[0]);
                    workingday.setStarttimeminute(begin[1]);
                    workingdayService.upsertWorkingday(workingday);
                }
            }

            if (form.isSaveAsFavorite()) {
                var fav = Favorite.builder()
                    .employeeorderId(employeeOrderId)
                    .hours((int) durationHours)
                    .minutes((int) durationMinutes)
                    .comment(form.getComment())
                    .build();
                favoriteService.addFavorite(fav);
            }

            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage(isEdit
                    ? "main.timereport.update.success.text"
                    : "main.timereport.create.success.text"));
            return "redirect:/dailyreport/daily?mode=daily&date=" + date + "&employeeContractId=" + ecId;

        } catch (ErrorCodeException ex) {
            var orders = customerorderService.getCustomerordersWithValidEmployeeOrders(ecId, date);
            var suborders = form.getOrderId() != null
                ? suborderService.getSuborderOptionsForForm(ecId, form.getOrderId(), date)
                : List.<SuborderOption>of();
            populateModel(model, form, orders, suborders, ecId, date, isEdit);
            model.addAttribute("errors", errorCodeViewHelper.toViewMessages(ex));
            return "dailyreport/timereport-form";
        }
    }

    private String reRenderFormWithError(Model model, TimereportForm form, long ecId, LocalDate date,
            boolean isEdit, String errorMessage) {
        var orders = customerorderService.getCustomerordersWithValidEmployeeOrders(ecId, date);
        var suborders = form.getOrderId() != null
            ? suborderService.getSuborderOptionsForForm(ecId, form.getOrderId(), date)
            : List.<SuborderOption>of();
        populateModel(model, form, orders, suborders, ecId, date, isEdit);
        model.addAttribute("errors", List.of(errorMessage));
        return "dailyreport/timereport-form";
    }

    private void populateModel(Model model, TimereportForm form, List<Customerorder> orders,
            List<SuborderOption> suborders, long ecId, LocalDate date, boolean isEdit) {
        model.addAttribute("timereportForm", form);
        model.addAttribute("orders", orders);
        model.addAttribute("suborders", suborders);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("todaysBookings",
            timereportService.getTimereportsByDateAndEmployeeContractId(ecId, date));
        model.addAttribute("recentComments", loadRecentComments(form));
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
        if (form.getEmployeeContractId() != null && form.getSuborderId() != null) {
            return timereportService.getRecentComments(form.getEmployeeContractId(), form.getSuborderId());
        }
        return List.of();
    }

    private long resolveContractId(Long requestedId, LocalDate date) {
        if (requestedId != null) return requestedId;
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
