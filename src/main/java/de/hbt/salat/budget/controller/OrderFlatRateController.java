package de.hbt.salat.budget.controller;

import static org.apache.commons.lang3.StringUtils.trimToNull;
import static de.hbt.salat.budget.controller.BudgetUiStateKeyContributor.CUSTOMER_ORDER_ID;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
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
import de.hbt.salat.budget.domain.FlatRateDueAmount;
import de.hbt.salat.budget.domain.FlatRateRhythm;
import de.hbt.salat.budget.domain.OrderFlatRate;
import de.hbt.salat.budget.domain.OrderFlatRateData;
import de.hbt.salat.budget.domain.OrderFlatRateInstalmentData;
import de.hbt.salat.budget.service.OrderFlatRateService;
import de.hbt.salat.budget.viewhelper.CustomerorderFilterOption;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.common.viewhelper.FilterHintViewHelper;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.order.viewhelper.CustomerorderViewHelper;

/**
 * Flat rates of an order (#972) — pauschale Erlöse.
 *
 * <p>Managers only, like the customer rates next to it: what an order is paid is a commercial
 * condition, not a controlling figure (#919).
 */
@Controller
@RequestMapping("/budget/flat-rate")
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class OrderFlatRateController {

    private final OrderFlatRateService orderFlatRateService;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final CustomerorderViewHelper customerorderViewHelper;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final FilterHintViewHelper filterHintViewHelper;
    private final MessageSourceAccessor messages;

    /**
     * The parameters carry the prefix {@code f} of every remembered filter (ADR-0022) and are
     * registered in {@link BudgetUiStateKeyContributor} — without that registration nothing is
     * remembered and the switch falls back to its default on every page view (#1098).
     *
     * <p>The switch has a key of its own, not the one of the rate list next door: the UiState
     * mapping is global, so a switch sharing a name with another list would share its remembered
     * value (#952). There is no switch for inactive flat rates — a flat rate is never inactive,
     * only its order can be (#1438).
     */
    @GetMapping
    public String list(@RequestParam(required = false) Long fBudgetCustomerOrderId,
                       @RequestParam(required = false) Boolean fFlatRateShowInactiveOrders,
                       Model model) {
        var inactiveOrders = Boolean.TRUE.equals(fFlatRateShowInactiveOrders);
        model.addAttribute("rows", orderFlatRateService.getRows(fBudgetCustomerOrderId, inactiveOrders));
        model.addAttribute("customerorderOptions", filterOptions());
        model.addAttribute("fBudgetCustomerOrderId", fBudgetCustomerOrderId);
        model.addAttribute("showInactiveOrders", inactiveOrders);
        return "budget/flat-rate-list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        addFormModel(model, new OrderFlatRateForm(), false);
        return "budget/flat-rate-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        addFormModel(model, formOf(orderFlatRateService.getById(id)), true);
        return "budget/flat-rate-form";
    }

    /**
     * The schedule of one definition and, for instalments, the payments it consists of. This is
     * where a set of instalments is maintained — they carry an amount each and cannot be entered in
     * the form of the definition.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Model model) {
        var flatRate = orderFlatRateService.getById(id);
        var dueAmounts = flatRate.dueAmountsWithin(flatRate.getValidFrom(), flatRate.getValidUntil());
        model.addAttribute("flatRate", flatRate);
        model.addAttribute("dueAmounts", dueAmounts);
        model.addAttribute("dueTotal", dueAmounts.stream().map(FlatRateDueAmount::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("instalmentForm", new OrderFlatRateInstalmentForm());
        return "budget/flat-rate-detail";
    }

    @PostMapping("/store")
    public String store(@ModelAttribute("flatRateForm") OrderFlatRateForm form,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        var error = validate(form);
        if (error != null) {
            model.addAttribute("formErrors", List.of(messages.getMessage(error)));
            addFormModel(model, form, !form.isNew());
            return "budget/flat-rate-form";
        }

        var data = new OrderFlatRateData(
            form.getCustomerorderId(),
            form.getSuborderId(),
            form.getOrderBudgetId(),
            trimToNull(form.getDescription()),
            form.getRhythm(),
            form.getAmountEuro(),
            form.getValidFrom(),
            form.getValidUntil()
        );

        try {
            if (form.isNew()) {
                var id = orderFlatRateService.save(data);
                // Instalments are the point of that rhythm and can only be entered once the
                // definition exists, so go where they are maintained rather than back to the list.
                // The filter hint belongs to the list and would be beside the point there.
                if (form.getRhythm() == FlatRateRhythm.INSTALMENTS) {
                    redirectAttributes.addFlashAttribute("toastSuccess",
                        messages.getMessage("main.flatrate.message.created"));
                    return "redirect:/budget/flat-rate/" + id;
                }
                filterHintViewHelper.addSuccess(redirectAttributes,
                    messages.getMessage("main.flatrate.message.created"), CUSTOMER_ORDER_ID);
            } else {
                orderFlatRateService.update(form.getId(), data);
                filterHintViewHelper.addSuccess(redirectAttributes,
                    messages.getMessage("main.flatrate.message.updated"), CUSTOMER_ORDER_ID);
            }
        } catch (ErrorCodeException ex) {
            model.addAttribute("formErrors",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).toList());
            addFormModel(model, form, !form.isNew());
            return "budget/flat-rate-form";
        }
        return "redirect:/budget/flat-rate";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirectAttributes) {
        try {
            orderFlatRateService.delete(id);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.flatrate.message.deleted"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
        }
        return "redirect:/budget/flat-rate";
    }

    @PostMapping("/{id}/instalments/add")
    public String addInstalment(@PathVariable long id,
                                @ModelAttribute("instalmentForm") OrderFlatRateInstalmentForm form,
                                RedirectAttributes redirectAttributes) {
        if (form.getAmountEuro() == null || form.getDue() == null) {
            redirectAttributes.addFlashAttribute("toastError",
                messages.getMessage("main.flatrate.instalment.error.required"));
            return "redirect:/budget/flat-rate/" + id;
        }
        try {
            orderFlatRateService.addInstalment(id, new OrderFlatRateInstalmentData(
                form.getAmountEuro(), form.getDue(), form.getComment()));
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.flatrate.instalment.message.added"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
        }
        return "redirect:/budget/flat-rate/" + id;
    }

    @PostMapping("/{id}/instalments/{instalmentId}/delete")
    public String deleteInstalment(@PathVariable long id, @PathVariable long instalmentId,
                                   RedirectAttributes redirectAttributes) {
        try {
            orderFlatRateService.removeInstalment(id, instalmentId);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.flatrate.instalment.message.deleted"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
        }
        return "redirect:/budget/flat-rate/" + id;
    }

    /**
     * Refills the suborder select and recomputes the preview of the due dates while the form is
     * being filled in. Both depend on fields of the form, and a monthly rate is the case where
     * seeing the dates before saving actually decides whether the entry is right.
     */
    @PostMapping("/refresh")
    public String refresh(@ModelAttribute("flatRateForm") OrderFlatRateForm form, Model model,
                          HttpServletRequest request) {
        addFormModel(model, form, !form.isNew());
        model.addAttribute("htmxRequest", "true".equals(request.getHeader("HX-Request")));
        return "budget/flat-rate-form";
    }

    /** The first of an exception's messages, resolved — or the generic one when it carries none. */
    private String firstMessageOf(ErrorCodeException ex) {
        return errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).findFirst()
            .orElse(messages.getMessage("main.general.error.unknown"));
    }

    /** The first message key the form violates, or {@code null} when it is complete. */
    private String validate(OrderFlatRateForm form) {
        if (form.getCustomerorderId() == null) {
            return "main.flatrate.error.order.required";
        }
        if (form.getRhythm() == null) {
            return "main.flatrate.error.rhythm.required";
        }
        if (form.getValidFrom() == null) {
            return "main.flatrate.error.validfrom.required";
        }
        if (form.needsAmount() && form.getAmountEuro() == null) {
            return "main.flatrate.error.amount.required";
        }
        if (form.needsValidUntil()) {
            // No open end, unlike an hourly rate: it would let a monthly flat rate run up an
            // unbounded revenue, and instalments would have no period to lie inside of.
            if (form.getValidUntil() == null) {
                return "main.flatrate.error.validuntil.required";
            }
            if (form.getValidFrom().isAfter(form.getValidUntil())) {
                return "main.flatrate.error.dates.invalid";
            }
        }
        return null;
    }

    private OrderFlatRateForm formOf(OrderFlatRate flatRate) {
        var form = new OrderFlatRateForm();
        form.setId(flatRate.getId());
        form.setCustomerorderId(flatRate.getCustomerorderId());
        form.setSuborderId(flatRate.getSuborderId());
        form.setOrderBudgetId(flatRate.getOrderBudgetId());
        form.setDescription(flatRate.getDescription());
        form.setRhythm(flatRate.getRhythm());
        form.setAmountEuro(flatRate.getAmount());
        form.setValidFrom(flatRate.getValidFrom());
        form.setValidUntil(flatRate.getValidUntil());
        return form;
    }

    /**
     * The customer orders offered in the list filter — those that actually carry a flat rate,
     * labelled like every other order select (→ {@link CustomerorderFilterOption}).
     */
    private List<CustomerorderFilterOption> filterOptions() {
        return orderFlatRateService.getCustomerordersWithFlatRate().stream()
            .map(order -> CustomerorderFilterOption.of(order, customerorderViewHelper))
            .toList();
    }

    private void addFormModel(Model model, OrderFlatRateForm form, boolean isEdit) {
        model.addAttribute("flatRateForm", form);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("rhythms", FlatRateRhythm.values());
        var customerorder = form.getCustomerorderId() == null
            ? null
            : customerorderService.getCustomerorderById(form.getCustomerorderId());
        model.addAttribute("customerorders",
            customerorderService.getSelectableCustomerorders(customerorder == null ? null : customerorder.getSign()));
        model.addAttribute("suborders", subordersOf(customerorder, form.getSuborderId()));
        // A single amount is due on one day, so the period the plan is matched against is the one
        // the saving will store, not the one the form shows (#1065).
        model.addAttribute("budgetPlans", orderFlatRateService.getSelectablePlans(
            form.getCustomerorderId(), form.getSuborderId(), form.getValidFrom(),
            form.needsValidUntil() ? form.getValidUntil() : form.getValidFrom(),
            form.getOrderBudgetId()));
        var preview = previewOf(form);
        model.addAttribute("previewDueAmounts", preview);
        model.addAttribute("previewTotal", preview.stream().map(FlatRateDueAmount::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        // Nothing to preview yet, and saying so beats an empty list that looks like a mistake.
        model.addAttribute("previewInstalmentsPending",
            form.getRhythm() == FlatRateRhythm.INSTALMENTS && form.isNew());
    }

    /**
     * The suborders of the selected order — empty while none is selected, because offering the
     * suborders of every order is a list of several thousand entries. The stored one is kept even
     * when it is hidden, or editing a flat rate would silently move it to the order level.
     */
    private List<Suborder> subordersOf(Customerorder customerorder, Long keepSuborderId) {
        return customerorder == null ? List.of()
            : suborderService.getSelectableSubordersByCustomerorderId(customerorder.getId(), keepSuborderId);
    }

    /**
     * What the definition in the form would put on the calendar. Computed on a record that is never
     * saved, so the preview cannot disagree with the schedule the controlling later expands — both
     * ask {@code OrderFlatRate} itself.
     *
     * <p>Instalments are the exception: they are stored, not derived, so an unsaved definition has
     * none to show yet.
     */
    private List<FlatRateDueAmount> previewOf(OrderFlatRateForm form) {
        if (form.getRhythm() == null || form.getValidFrom() == null
            || (form.needsAmount() && form.getAmountEuro() == null)
            || (form.needsValidUntil() && form.getValidUntil() == null)
            || (form.needsValidUntil() && form.getValidFrom().isAfter(form.getValidUntil()))) {
            return List.of();
        }
        var preview = new OrderFlatRate();
        preview.setRhythm(form.getRhythm());
        preview.setAmount(form.getAmountEuro());
        preview.setValidFrom(form.getValidFrom());
        preview.setValidUntil(form.needsValidUntil() ? form.getValidUntil() : form.getValidFrom());
        if (form.getRhythm() == FlatRateRhythm.INSTALMENTS && !form.isNew()) {
            preview.getInstalments().addAll(orderFlatRateService.getById(form.getId()).getInstalments());
        }
        return preview.dueAmountsWithin(preview.getValidFrom(), preview.getValidUntil());
    }

}
