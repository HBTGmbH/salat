package org.tb.budget.controller;

import static org.apache.commons.lang3.StringUtils.trimToNull;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
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
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetAdjustmentData;
import org.tb.budget.domain.OrderBudgetData;
import org.tb.budget.domain.OrderBudgetScopeEntryData;
import org.tb.budget.domain.ProgressMode;
import org.tb.budget.service.OrderBudgetService;
import org.tb.budget.service.TimereportBudgetAssignmentService;
import org.tb.budget.viewhelper.AssignedTimereportViewHelper;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.util.DurationUtils;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

@Controller
@RequestMapping("/budget")
@RequiredArgsConstructor
@Authorized(requireUnrestricted = true)
public class BudgetController {

    /**
     * How many assigned bookings the detail page renders. Beyond this the page says how many were
     * left out and offers the period filter — a silently truncated list would read as complete.
     */
    private static final int ASSIGNED_LIST_LIMIT = 200;

    private final OrderBudgetService orderBudgetService;
    private final TimereportBudgetAssignmentService assignmentService;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final AuthorizedUser authorizedUser;
    private final BudgetAuthorization budgetAuthorization;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final MessageSourceAccessor messages;

    @GetMapping
    public String list(@RequestParam(required = false) String coSign,
                       @RequestParam(required = false) Boolean showInactive,
                       Model model) {
        List<OrderBudget> budgets;
        if (coSign != null && !coSign.isBlank()) {
            budgets = orderBudgetService.getVisibleByCustomerorderSign(
                coSign, Boolean.TRUE.equals(showInactive));
        } else {
            budgets = orderBudgetService.getAllVisible();
            if (!Boolean.TRUE.equals(showInactive)) {
                budgets = budgets.stream().filter(b -> Boolean.TRUE.equals(b.getActive())).toList();
            }
        }
        model.addAttribute("budgets", budgets);
        model.addAttribute("coSign", coSign);
        model.addAttribute("showInactive", Boolean.TRUE.equals(showInactive));
        model.addAttribute("isManager", authorizedUser.isManager());
        model.addAttribute("customerorders", budgetAuthorization.authorizedCustomerorders());
        return "budget/budget-list";
    }

    @Authorized(requiresManager = true)
    @GetMapping("/create")
    public String createForm(Model model) {
        var form = new OrderBudgetForm();
        addFormModel(model, form, false);
        return "budget/budget-form";
    }

    @Authorized(requiresManager = true)
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        var budget = orderBudgetService.getById(id);
        var form = new OrderBudgetForm();
        form.setId(budget.getId());
        form.setName(budget.getName());
        form.setCustomerorderSign(budget.getCustomerorderSign());
        form.setSuborderSign(budget.getSuborderSign());
        form.setValidFrom(budget.getValidFrom());
        form.setValidUntil(budget.getValidUntil());
        form.setActive(budget.getActive());
        form.setAlertThresholdPercent(budget.getAlertThresholdPercent());
        form.setProgressMode(budget.getProgressMode());
        addFormModel(model, form, true);
        return "budget/budget-form";
    }

    @Authorized(requiresManager = true)
    @PostMapping("/store")
    public String store(@ModelAttribute("budgetForm") OrderBudgetForm form,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (form.getName() == null || form.getName().isBlank()) {
            model.addAttribute("formErrors", List.of(messages.getMessage("main.budget.error.name.required")));
            addFormModel(model, form, !form.isNew());
            return "budget/budget-form";
        }
        if (form.getCustomerorderSign() == null || form.getCustomerorderSign().isBlank()) {
            model.addAttribute("formErrors", List.of(messages.getMessage("main.budget.error.order.required")));
            addFormModel(model, form, !form.isNew());
            return "budget/budget-form";
        }
        if (form.getValidFrom() == null || form.getValidUntil() == null) {
            model.addAttribute("formErrors", List.of(messages.getMessage("main.budget.error.dates.required")));
            addFormModel(model, form, !form.isNew());
            return "budget/budget-form";
        }
        if (form.getValidFrom().isAfter(form.getValidUntil())) {
            model.addAttribute("formErrors", List.of(messages.getMessage("main.budget.error.dates.invalid")));
            addFormModel(model, form, !form.isNew());
            return "budget/budget-form";
        }

        var data = new OrderBudgetData(
            form.getName(),
            form.getCustomerorderSign(),
            trimToNull(form.getSuborderSign()),
            form.getValidFrom(),
            form.getValidUntil(),
            Boolean.TRUE.equals(form.getActive()),
            form.getAlertThresholdPercent(),
            form.getProgressMode()
        );

        try {
            if (form.isNew()) {
                orderBudgetService.create(data);
                redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.budget.message.created"));
            } else {
                orderBudgetService.update(form.getId(), data);
                redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.budget.message.updated"));
            }
        } catch (ErrorCodeException ex) {
            model.addAttribute("formErrors",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).toList());
            addFormModel(model, form, !form.isNew());
            return "budget/budget-form";
        }
        return "redirect:/budget";
    }

    @Authorized(requiresManager = true)
    @PostMapping("/{id}/toggle-active")
    public String toggleActive(@PathVariable long id, RedirectAttributes redirectAttributes) {
        try {
            var budget = orderBudgetService.getById(id);
            var newActive = !Boolean.TRUE.equals(budget.getActive());
            orderBudgetService.setActive(id, newActive);
            redirectAttributes.addFlashAttribute("toastSuccess", newActive
                ? messages.getMessage("main.budget.message.activated")
                : messages.getMessage("main.budget.message.deactivated"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).findFirst()
                    .orElse(messages.getMessage("main.general.error.unknown")));
        }
        return "redirect:/budget";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id,
                         @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate until,
                         Model model) {
        var budget = orderBudgetService.getById(id);
        model.addAttribute("budget", budget);
        model.addAttribute("adjustmentForm", new OrderBudgetAdjustmentForm());
        model.addAttribute("scopeEntryForm", new OrderBudgetScopeEntryForm());
        model.addAttribute("progressModes", ProgressMode.values());
        model.addAttribute("isManager", authorizedUser.isManager());
        addAssignedTimereports(budget, from, until, model);
        return "budget/budget-detail";
    }

    /**
     * The bookings assigned to the plan, and where they could be moved to (#912).
     *
     * <p>The period defaults to the plan's validity, which is where its bookings are. A plan can
     * hold hundreds of them, so the list is capped and says so — the alternative would be a page
     * that takes seconds to render and is unusable exactly for the plans that need attention.
     */
    private void addAssignedTimereports(OrderBudget budget, LocalDate from, LocalDate until, Model model) {
        var periodFrom = from != null ? from : budget.getValidFrom();
        var periodUntil = until != null ? until : budget.getValidUntil();
        var assigned = assignmentService.getAssignedTimereports(budget.getId(), periodFrom, periodUntil);

        model.addAttribute("assignedFrom", periodFrom);
        model.addAttribute("assignedUntil", periodUntil);
        model.addAttribute("assignedCount", assigned.size());
        model.addAttribute("assignedHours", DurationUtils.format(assigned.stream()
            .map(TimereportDTO::getDuration)
            .reduce(Duration.ZERO, Duration::plus)));
        model.addAttribute("assignedTimereports",
            AssignedTimereportViewHelper.from(assigned.stream().limit(ASSIGNED_LIST_LIMIT).toList()));
        model.addAttribute("assignedLimit", ASSIGNED_LIST_LIMIT);
        model.addAttribute("assignedTruncated", assigned.size() > ASSIGNED_LIST_LIMIT);
        // Only the other active plans of the same order are possible targets: an inactive plan
        // cannot hold bookings, and a plan of another order can never cover them.
        model.addAttribute("moveTargets",
            orderBudgetService.getActiveByCustomerorderSign(budget.getCustomerorderSign()).stream()
                .filter(other -> !other.getId().equals(budget.getId()))
                .toList());
    }

    /**
     * Moves the selected bookings to another plan, or dissolves their assignment when no target was
     * chosen. Rejected as a whole if one booking does not fit the target, so the error names what is
     * wrong instead of leaving a half-moved selection behind.
     */
    @Authorized(requiresManager = true)
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/assignments/move")
    public String moveAssignments(@PathVariable long id,
                                  @RequestParam(required = false) List<Long> timereportIds,
                                  @RequestParam(required = false) Long targetBudgetId,
                                  RedirectAttributes redirectAttributes) {
        var selected = timereportIds == null ? List.<Long>of() : timereportIds;
        if (selected.isEmpty()) {
            redirectAttributes.addFlashAttribute("toastError",
                messages.getMessage("main.budget.assignments.error.noselection"));
            return "redirect:/budget/" + id;
        }
        try {
            assignmentService.move(selected, targetBudgetId);
            redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage(
                targetBudgetId == null
                    ? "main.budget.assignments.message.unassigned"
                    : "main.budget.assignments.message.moved",
                new Object[] {selected.size()}));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).findFirst()
                    .orElse(messages.getMessage("main.general.error.unknown")));
        }
        return "redirect:/budget/" + id;
    }

    @Authorized(requiresManager = true)
    @PostMapping("/{id}/adjustments/add")
    public String addAdjustment(@PathVariable long id,
                                @ModelAttribute("adjustmentForm") OrderBudgetAdjustmentForm form,
                                RedirectAttributes redirectAttributes) {
        try {
            orderBudgetService.addAdjustment(id, new OrderBudgetAdjustmentData(
                form.getAmount(), form.getEffective(), form.getComment()));
            redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.budget.adjustment.message.added"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).findFirst()
                    .orElse(messages.getMessage("main.general.error.unknown")));
        }
        return "redirect:/budget/" + id;
    }

    @Authorized(requiresManager = true)
    @PostMapping("/{id}/adjustments/{adjId}/delete")
    public String deleteAdjustment(@PathVariable long id, @PathVariable long adjId,
                                   RedirectAttributes redirectAttributes) {
        try {
            orderBudgetService.removeAdjustment(id, adjId);
            redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.budget.adjustment.message.deleted"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).findFirst()
                    .orElse(messages.getMessage("main.general.error.unknown")));
        }
        return "redirect:/budget/" + id;
    }

    @Authorized(requiresManager = true)
    @PostMapping("/{id}/scope-entries/add")
    public String addScopeEntry(@PathVariable long id,
                                @ModelAttribute("scopeEntryForm") OrderBudgetScopeEntryForm form,
                                RedirectAttributes redirectAttributes) {
        try {
            orderBudgetService.addScopeEntry(id, new OrderBudgetScopeEntryData(
                form.getRefdate(), form.getPercent(), form.getComment()));
            redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.budget.scope.message.added"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).findFirst()
                    .orElse(messages.getMessage("main.general.error.unknown")));
        }
        return "redirect:/budget/" + id;
    }

    @Authorized(requiresManager = true)
    @PostMapping("/{id}/scope-entries/{entryId}/delete")
    public String deleteScopeEntry(@PathVariable long id, @PathVariable long entryId,
                                   RedirectAttributes redirectAttributes) {
        try {
            orderBudgetService.removeScopeEntry(id, entryId);
            redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.budget.scope.message.deleted"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).findFirst()
                    .orElse(messages.getMessage("main.general.error.unknown")));
        }
        return "redirect:/budget/" + id;
    }

    /**
     * Refills the suborder list when the customer order changes. Offering the suborders of every
     * order let a budget be pointed at a suborder outside the chosen order — rejected on save since
     * #890, but only after the user had already picked it.
     */
    @Authorized(requiresManager = true)
    @PostMapping("/suborders")
    public String suborders(@ModelAttribute("budgetForm") OrderBudgetForm form, Model model,
                            HttpServletRequest request) {
        form.setSuborderSign(null); // the previous pick belongs to the order that was just replaced
        addFormModel(model, form, !form.isNew());
        model.addAttribute("htmxRequest", "true".equals(request.getHeader("HX-Request")));
        model.addAttribute("subordersChanged", true);
        return "budget/budget-form";
    }

    private void addFormModel(Model model, OrderBudgetForm form, boolean isEdit) {
        model.addAttribute("budgetForm", form);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("customerorders",
            customerorderService.getSelectableCustomerorders(form.getCustomerorderSign()));
        model.addAttribute("suborders",
            subordersOf(form.getCustomerorderSign(), form.getSuborderSign()));
        model.addAttribute("progressModes", ProgressMode.values());
    }

    /**
     * The suborders of the selected customer order — empty while none is selected. The suborder the
     * budget already references stays in the list even once it is hidden, so that editing does not
     * drop it.
     */
    private List<Suborder> subordersOf(String customerorderSign, String keepSuborderSign) {
        if (trimToNull(customerorderSign) == null) {
            return List.of();
        }
        var customerorder = customerorderService.getCustomerorderBySign(customerorderSign);
        return customerorder == null ? List.of()
            : suborderService.getSelectableFirstLevelSubordersByCustomerorderId(customerorder.getId(), keepSuborderSign);
    }

}
