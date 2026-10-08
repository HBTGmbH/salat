package de.hbt.salat.budget.controller;

import static java.util.stream.Collectors.toMap;
import static de.hbt.salat.budget.controller.BudgetUiStateKeyContributor.CUSTOMER_ORDER_ID;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.domain.BudgetEmployeeSign;
import de.hbt.salat.budget.domain.CalculationLineData;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.OrderBudgetAdjustmentData;
import de.hbt.salat.budget.domain.OrderBudgetData;
import de.hbt.salat.budget.domain.OrderBudgetScopeEntryData;
import de.hbt.salat.budget.domain.ProgressMode;
import de.hbt.salat.budget.service.BudgetEmployeeService;
import de.hbt.salat.budget.service.FixedPriceCalculationService;
import de.hbt.salat.budget.service.OrderBudgetService;
import de.hbt.salat.budget.service.OrderFlatRateService;
import de.hbt.salat.budget.service.OrderPricingService;
import de.hbt.salat.budget.service.TimereportBudgetAssignmentService;
import de.hbt.salat.budget.viewhelper.AssignedTimereportViewHelper;
import de.hbt.salat.budget.viewhelper.BudgetEmployeesViewHelper;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.common.util.DurationUtils;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.common.viewhelper.FilterHintViewHelper;
import de.hbt.salat.common.viewhelper.NoticeViewHelper;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

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
    private final BudgetEmployeeService budgetEmployeeService;
    private final OrderPricingService orderPricingService;
    private final OrderFlatRateService orderFlatRateService;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final AuthorizedUser authorizedUser;
    private final BudgetAuthorization budgetAuthorization;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final FilterHintViewHelper filterHintViewHelper;
    private final NoticeViewHelper noticeViewHelper;
    private final FixedPriceCalculationService fixedPriceCalculationService;
    private final MessageSourceAccessor messages;

    /**
     * The parameter is {@code fBudgetShowInactive} rather than {@code showInactive} because the
     * UiState mapping is global: the rate list has a switch of the same name that means something
     * else, and both would otherwise share one remembered value (#952).
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long fBudgetCustomerOrderId,
                       @RequestParam(required = false) Boolean fBudgetShowInactive,
                       Model model) {
        List<OrderBudget> budgets;
        if (fBudgetCustomerOrderId != null) {
            budgets = orderBudgetService.getVisibleByCustomerorderId(
                fBudgetCustomerOrderId, Boolean.TRUE.equals(fBudgetShowInactive));
        } else {
            budgets = orderBudgetService.getAllVisible();
            if (!Boolean.TRUE.equals(fBudgetShowInactive)) {
                budgets = budgets.stream().filter(b -> Boolean.TRUE.equals(b.getActive())).toList();
            }
        }
        model.addAttribute("budgets", byOrderSignThenValidFrom(budgets));
        model.addAttribute("fBudgetCustomerOrderId", fBudgetCustomerOrderId);
        model.addAttribute("showInactive", Boolean.TRUE.equals(fBudgetShowInactive));
        model.addAttribute("isManager", authorizedUser.isManager());
        model.addAttribute("customerorders", budgetAuthorization.selectableCustomerorders(fBudgetCustomerOrderId));
        model.addAttribute("employeesByBudget", employeeSignsOf(budgets));
        return "budget/budget-list";
    }

    /**
     * By the sign the order has today, then by start of validity (#1212).
     */
    static List<OrderBudget> byOrderSignThenValidFrom(List<OrderBudget> budgets) {
        Function<OrderBudget, String> orderSign = budget -> budget.getCustomerorder().getSign();
        return budgets.stream()
            .sorted(Comparator.comparing(orderSign, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(OrderBudget::getValidFrom, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /**
     * Who booked on each plan (#964) — one aggregate query for the whole page, not one per row.
     * Asking {@code getAssignedBookings} per row would be two statements <em>and</em> the bookings
     * of the plan's whole order every time, which is the pattern the budget dashboard broke on
     * (→ {@code docs/performance-tips.md}).
     */
    private Map<Long, List<BudgetEmployeeSign>> employeeSignsOf(List<OrderBudget> budgets) {
        return signsByBudget(budgets, budgetEmployeeService.employeesOf(budgets));
    }

    /**
     * Every plan of the page gets an entry, the ones without a booking an empty list (#1047): the
     * aggregate holds only plans somebody booked on, and the cell must not run onto a {@code null}.
     *
     * <p>The signs are handed on in the order the query delivered them — alphabetical by sign
     * ({@code BudgetEmployeeQueryTest}) — and complete: the column lists everybody, however many
     * that is, and lets the badges wrap inside the cell instead.
     */
    static Map<Long, List<BudgetEmployeeSign>> signsByBudget(
        List<OrderBudget> budgets, Map<Long, List<BudgetEmployeeSign>> byBudget) {
        return budgets.stream().collect(toMap(OrderBudget::getId,
            budget -> byBudget.getOrDefault(budget.getId(), List.of()),
            (first, second) -> first));
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
        form.setCustomerorderId(budget.getCustomerorderId());
        form.setSuborderId(budget.getSuborderId());
        form.setValidFrom(budget.getValidFrom());
        form.setValidUntil(budget.getValidUntil());
        form.setActive(budget.getActive());
        form.setAlertThresholdPercent(budget.getAlertThresholdPercent());
        form.setProgressMode(budget.getProgressMode());
        form.setFixedPrice(budget.isFixedPrice());
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
        if (form.getCustomerorderId() == null) {
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
            form.getCustomerorderId(),
            form.getSuborderId(),
            form.getValidFrom(),
            form.getValidUntil(),
            Boolean.TRUE.equals(form.getActive()),
            form.getAlertThresholdPercent(),
            form.getProgressMode(),
            Boolean.TRUE.equals(form.getFixedPrice())
        );

        try {
            long id;
            var notices = new ArrayList<ServiceFeedbackMessage>();
            if (form.isNew()) {
                id = orderBudgetService.create(data).getId();
                filterHintViewHelper.addSuccess(redirectAttributes,
                    messages.getMessage("main.budget.message.created"), CUSTOMER_ORDER_ID);
            } else {
                id = form.getId();
                // calculation lines the edit left without a place are removed and named (#1404)
                notices.addAll(orderBudgetService.update(id, data));
                filterHintViewHelper.addSuccess(redirectAttributes,
                    messages.getMessage("main.budget.message.updated"), CUSTOMER_ORDER_ID);
            }
            // A customer rate above 0 EUR in the scope of a fixed price counts revenue twice (#1404);
            // the plan is saved all the same, and the rates are named below the success message.
            notices.addAll(fixedPriceCalculationService.noticesForPlan(id));
            noticeViewHelper.addNotices(redirectAttributes, notices);
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
                         @RequestParam(required = false) Long editLine,
                         Model model) {
        var budget = orderBudgetService.getById(id);
        model.addAttribute("budget", budget);
        addFixedPrice(budget, editLine, model);
        model.addAttribute("adjustmentForm", new OrderBudgetAdjustmentForm());
        model.addAttribute("scopeEntryForm", new OrderBudgetScopeEntryForm());
        model.addAttribute("progressModes", ProgressMode.values());
        model.addAttribute("isManager", authorizedUser.isManager());
        // The conditions negotiated for this work package (#1065). Shown to everybody who reaches
        // the plan, like the rates of the "Mitarbeitende" card; only editing them stays with
        // managers, which is where the links lead.
        var boundPricings = orderPricingService.getByOrderBudgetId(id);
        model.addAttribute("boundPricings", boundPricings);
        var boundFlatRates = orderFlatRateService.getByOrderBudgetId(id);
        model.addAttribute("boundFlatRates", boundFlatRates);
        addAssignedTimereports(budget, from, until, model);
        if (authorizedUser.isManager()) {
            model.addAttribute("deletion", orderBudgetService.deletionScope(id));
        }
        return "budget/budget-detail";
    }

    /**
     * Deletes the plan once its name has been typed twice (#1424); the service compares the name
     * again. A mismatch leads back to the plan, which is still there.
     */
    @Authorized(requiresManager = true)
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, @RequestParam String confirmName,
                         RedirectAttributes redirectAttributes) {
        try {
            orderBudgetService.delete(id, confirmName);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.budget.message.deleted", new Object[] {confirmName}));
            return "redirect:/budget";
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).findFirst()
                    .orElse(messages.getMessage("main.general.error.unknown")));
            return "redirect:/budget/" + id;
        }
    }

    /**
     * The bookings assigned to the plan, and where they could be moved to (#912).
     *
     * <p>The period defaults to the plan's validity, which is where its bookings are. A plan can
     * hold thousands of them, so the list is capped and says so — the alternative would be a page
     * that takes seconds to render and is unusable exactly for the plans that need attention.
     *
     * <p>Sorting, capping and the figures of the header all come out of the database already shaped
     * (#997); nothing is counted or reordered here.
     *
     * <p>The "Mitarbeitende" card hangs on the same period (#964). It is resolved in one pass with
     * the rates of the rendered rows, so the card and the rows cannot name different rates for the
     * same work — and it reads the whole period rather than the capped list, which would understate
     * the hours of exactly the plans that need attention.
     */
    private void addAssignedTimereports(OrderBudget budget, LocalDate from, LocalDate until, Model model) {
        var periodFrom = from != null ? from : budget.getValidFrom();
        var periodUntil = until != null ? until : budget.getValidUntil();
        var assigned = assignmentService.getAssignedBookings(
            budget.getId(), periodFrom, periodUntil, ASSIGNED_LIST_LIMIT);
        var rates = budgetEmployeeService.resolve(budget, periodFrom, periodUntil, assigned.newest());

        model.addAttribute("assignedFrom", periodFrom);
        model.addAttribute("assignedUntil", periodUntil);
        model.addAttribute("assignedCount", assigned.count());
        model.addAttribute("assignedHours", DurationUtils.format(assigned.totalDuration()));
        model.addAttribute("assignedTimereports",
            AssignedTimereportViewHelper.from(assigned.newest(), rates));
        model.addAttribute("employees", BudgetEmployeesViewHelper.from(rates.employees()));
        model.addAttribute("assignedLimit", ASSIGNED_LIST_LIMIT);
        model.addAttribute("assignedTruncated", assigned.truncated());
        // Only the other active plans of the same order are possible targets: an inactive plan
        // cannot hold bookings, and a plan of another order can never cover them.
        model.addAttribute("moveTargets", budget.getCustomerorderId() == null
            ? List.of()
            : orderBudgetService.getActiveByCustomerorderId(budget.getCustomerorderId()).stream()
                .filter(other -> !other.getId().equals(budget.getId()))
                .toList());
    }

    /**
     * Moves the selected bookings to another plan, or dissolves their assignment when no target was
     * chosen. Rejected as a whole if one booking does not fit the target, so the error names what is
     * wrong instead of leaving a half-moved selection behind.
     */
    @Authorized(requiresManager = true)
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
     * The calculation of a fixed-price plan with what was booked against it, and the customer rates
     * that would count its revenue twice (#1404). The page reads the plan up to today: every booking
     * assigned to it, whatever period the booking list below is narrowed to — the consumption is
     * measured against the whole calculation. Costs stay in the controlling.
     *
     * <p>{@code editLine} names the line the form at the foot of the card is filled with; without it
     * the form adds a new one.
     */
    private void addFixedPrice(OrderBudget budget, Long editLine, Model model) {
        if (!budget.isFixedPrice()) {
            return;
        }
        // Up to today: what has fallen due and been booked so far, not what the plan holds until its end.
        var today = DateUtils.today();
        var until = budget.getValidUntil().isBefore(today) ? budget.getValidUntil() : today;
        model.addAttribute("fixedPrice", fixedPriceCalculationService.evaluate(budget, until, false).orElse(null));
        model.addAttribute("conflictingRates", fixedPriceCalculationService.getConflictingRates(budget));
        if (!authorizedUser.isManager()) {
            return;
        }
        var form = new CalculationLineForm();
        budget.getCalculations().stream()
            .filter(line -> line.getId().equals(editLine))
            .findFirst()
            .ifPresent(line -> {
                form.setId(line.getId());
                form.setSuborderId(line.getSuborderId());
                form.setCategoryId(line.getCategoryId());
                form.setHours(BigDecimal.valueOf(line.getCalculatedHours().toMinutes())
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
            });
        model.addAttribute("calculationForm", form);
        model.addAttribute("calculationSuborders",
            fixedPriceCalculationService.getCalculableSuborders(budget, form.getSuborderId()));
        model.addAttribute("calculationCategories", fixedPriceCalculationService.getCategories());
    }

    @Authorized(requiresManager = true)
    @PostMapping("/{id}/calculations/store")
    public String storeCalculationLine(@PathVariable long id,
                                       @ModelAttribute("calculationForm") CalculationLineForm form,
                                       RedirectAttributes redirectAttributes) {
        try {
            fixedPriceCalculationService.storeLine(id, new CalculationLineData(form.getId(), form.getSuborderId(),
                form.getCategoryId(), form.getHours()));
            redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage(form.getId() == null
                ? "main.budget.calculation.message.added" : "main.budget.calculation.message.updated"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).findFirst()
                    .orElse(messages.getMessage("main.general.error.unknown")));
        }
        return "redirect:/budget/" + id;
    }

    @Authorized(requiresManager = true)
    @PostMapping("/{id}/calculations/{lineId}/delete")
    public String deleteCalculationLine(@PathVariable long id, @PathVariable long lineId,
                                        RedirectAttributes redirectAttributes) {
        try {
            fixedPriceCalculationService.removeLine(id, lineId);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.budget.calculation.message.deleted"));
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
        form.setSuborderId(null); // the previous pick belongs to the order that was just replaced
        addFormModel(model, form, !form.isNew());
        model.addAttribute("htmxRequest", "true".equals(request.getHeader("HX-Request")));
        model.addAttribute("subordersChanged", true);
        return "budget/budget-form";
    }

    private void addFormModel(Model model, OrderBudgetForm form, boolean isEdit) {
        model.addAttribute("budgetForm", form);
        model.addAttribute("isEdit", isEdit);
        var customerorder = form.getCustomerorderId() == null
            ? null
            : customerorderService.getCustomerorderById(form.getCustomerorderId());
        model.addAttribute("customerorders",
            customerorderService.getSelectableCustomerorders(customerorder == null ? null : customerorder.getSign()));
        model.addAttribute("suborders", subordersOf(customerorder, form.getSuborderId()));
        model.addAttribute("progressModes", ProgressMode.values());
        // The level in force for the selected order: overlaps within a level are fine, mixing two
        // levels is what gets rejected (#914, #1004). Since the list offers the whole suborder tree,
        // this is the only place the person sees which level the next plan has to match.
        model.addAttribute("currentLevel",
            customerorder == null ? null : orderBudgetService.currentLevel(customerorder.getId()));
    }

    /**
     * The suborders of the selected customer order, at any depth (#1004) — empty while none is
     * selected. The suborder the budget already references stays in the list even once it is hidden,
     * so that editing does not drop it.
     */
    private List<Suborder> subordersOf(Customerorder customerorder, Long keepSuborderId) {
        return customerorder == null ? List.of()
            : suborderService.getSelectableSubordersByCustomerorderId(customerorder.getId(), keepSuborderId);
    }

}
