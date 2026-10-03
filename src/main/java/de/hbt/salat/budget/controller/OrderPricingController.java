package de.hbt.salat.budget.controller;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static de.hbt.salat.budget.controller.BudgetUiStateKeyContributor.CUSTOMER_ORDER_SIGN;

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
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.budget.domain.OrderPricingData;
import de.hbt.salat.budget.service.OrderPricingService;
import de.hbt.salat.budget.viewhelper.CustomerorderFilterOption;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.common.viewhelper.FilterHintViewHelper;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.order.viewhelper.CustomerorderViewHelper;

@Controller
@RequestMapping("/budget/pricing")
@RequiredArgsConstructor
// Customer rates are commercial conditions, not controlling figures — managers only (#919).
@Authorized(requiresManager = true)
public class OrderPricingController {

    private final OrderPricingService orderPricingService;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final EmployeeService employeeService;
    private final CustomerorderViewHelper customerorderViewHelper;
    private final AuthorizedUser authorizedUser;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final FilterHintViewHelper filterHintViewHelper;
    private final MessageSourceAccessor messages;

    /**
     * The parameters are prefixed rather than plain {@code showInactive} / {@code showInactiveOrders}
     * because the UiState mapping is global: the plan list has a switch of the same name that means
     * something else, and both would otherwise share one remembered value (#952).
     *
     * <p>The two switches are independent (#957): one is about the validity of the rate, the other
     * about the validity of its order. A rate can have expired while its order runs on, and a
     * current rate can hang off an order that ended last year.
     */
    @GetMapping
    public String list(@RequestParam(required = false) String fCustomerOrderSign,
                       @RequestParam(required = false) Boolean fPricingShowInactive,
                       @RequestParam(required = false) Boolean fPricingShowInactiveOrders,
                       Model model) {
        var inactive = Boolean.TRUE.equals(fPricingShowInactive);
        var inactiveOrders = Boolean.TRUE.equals(fPricingShowInactiveOrders);
        // The rows name their order by sign; description, customer and validity hang off the order.
        model.addAttribute("rows", orderPricingService.getRows(fCustomerOrderSign, inactive, inactiveOrders));
        model.addAttribute("customerorderOptions", filterOptions());
        model.addAttribute("fCustomerOrderSign", fCustomerOrderSign);
        model.addAttribute("showInactive", inactive);
        model.addAttribute("showInactiveOrders", inactiveOrders);
        model.addAttribute("isManager", authorizedUser.isManager());
        return "budget/pricing-list";
    }

    /**
     * All three parameters only prefill, and all are optional (#964). The "Mitarbeitende" card of a
     * budget plan links here for a person whose work has no condition at all — order and person are
     * known there, the rate and its validity are not.
     *
     * <p>That link names the order as {@code customerorderId}, the form field, not as the filter
     * parameter: a button that opens a form must not change the filter of the list behind it
     * (ADR-0023). Where it brings no order, the one the list is filtered to prefills the form — the
     * filter speaks in signs, the form in ids (#1212).
     */
    @Authorized(requiresManager = true)
    @GetMapping("/create")
    public String createForm(@RequestParam(required = false) Long customerorderId,
                             @RequestParam(required = false) String fCustomerOrderSign,
                             @RequestParam(required = false) Long employeeId,
                             Model model) {
        var form = new OrderPricingForm();
        form.setCustomerorderId(customerorderId != null ? customerorderId : customerorderIdOf(fCustomerOrderSign));
        form.setEmployeeId(employeeId);
        addFormModel(model, form, false);
        return "budget/pricing-form";
    }

    @Authorized(requiresManager = true)
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        var pricing = orderPricingService.getById(id);
        var form = new OrderPricingForm();
        form.setId(pricing.getId());
        form.setCustomerorderId(pricing.getCustomerorderId());
        form.setSuborderSign(pricing.getSuborderSign());
        form.setEmployeeId(pricing.getEmployeeId());
        form.setOrderBudgetId(pricing.getOrderBudgetId());
        form.setDescription(pricing.getDescription());
        form.setPriceEuro(new BigDecimal(pricing.getPriceCentsPerHour()).movePointLeft(2));
        form.setValidFrom(pricing.getValidFrom());
        var until = pricing.getValidUntil();
        if (until != null && until.getYear() != 2999) {
            form.setValidUntil(until);
        }
        addFormModel(model, form, true);
        return "budget/pricing-form";
    }

    @Authorized(requiresManager = true)
    @PostMapping("/store")
    public String store(@ModelAttribute("pricingForm") OrderPricingForm form,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (form.getCustomerorderId() == null) {
            model.addAttribute("formErrors", List.of(messages.getMessage("main.pricing.error.order.required")));
            addFormModel(model, form, !form.isNew());
            return "budget/pricing-form";
        }
        if (form.getPriceEuro() == null) {
            model.addAttribute("formErrors", List.of(messages.getMessage("main.pricing.error.price.required")));
            addFormModel(model, form, !form.isNew());
            return "budget/pricing-form";
        }
        if (form.getValidFrom() == null) {
            model.addAttribute("formErrors", List.of(messages.getMessage("main.pricing.error.validfrom.required")));
            addFormModel(model, form, !form.isNew());
            return "budget/pricing-form";
        }
        if (form.getValidUntil() != null && form.getValidFrom().isAfter(form.getValidUntil())) {
            model.addAttribute("formErrors", List.of(messages.getMessage("main.pricing.error.dates.invalid")));
            addFormModel(model, form, !form.isNew());
            return "budget/pricing-form";
        }

        var data = new OrderPricingData(
            form.getCustomerorderId(),
            trimToNull(form.getSuborderSign()),
            form.getEmployeeId(),
            form.getOrderBudgetId(),
            trimToNull(form.getDescription()),
            form.getPriceEuro().movePointRight(2).intValue(),
            form.getValidFrom(),
            form.getValidUntil()
        );

        try {
            if (form.isNew()) {
                orderPricingService.save(data);
                filterHintViewHelper.addSuccess(redirectAttributes,
                    messages.getMessage("main.pricing.message.created"), CUSTOMER_ORDER_SIGN);
            } else {
                orderPricingService.update(form.getId(), data);
                filterHintViewHelper.addSuccess(redirectAttributes,
                    messages.getMessage("main.pricing.message.updated"), CUSTOMER_ORDER_SIGN);
            }
        } catch (ErrorCodeException ex) {
            model.addAttribute("formErrors",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).toList());
            addFormModel(model, form, !form.isNew());
            return "budget/pricing-form";
        }
        return "redirect:/budget/pricing";
    }

    @Authorized(requiresManager = true)
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirectAttributes) {
        try {
            orderPricingService.delete(id);
            redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.pricing.message.deleted"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).findFirst()
                    .orElse(messages.getMessage("main.general.error.unknown")));
        }
        return "redirect:/budget/pricing";
    }

    /**
     * Refills the suborder picker when the customer order changes, and the budget plans with it.
     * The picker only prefills the pattern, but offering the suborders of every order would make it
     * useless — and it is a list of several thousand entries.
     *
     * <p>The plan select depends on more fields than the picker does — order, pattern and validity
     * (#1065) — so every one of them triggers this same call. One endpoint for both keeps the two
     * selects from drifting apart while the form is being filled in.
     */
    @Authorized(requiresManager = true)
    @PostMapping("/suborders")
    public String suborders(@ModelAttribute("pricingForm") OrderPricingForm form, Model model,
                            HttpServletRequest request) {
        addFormModel(model, form, !form.isNew());
        model.addAttribute("htmxRequest", "true".equals(request.getHeader("HX-Request")));
        model.addAttribute("subordersChanged", true);
        return "budget/pricing-form";
    }

    /**
     * The customer orders offered in the list filter — those that actually carry a rate, labelled
     * like every other order select. The signs come from the pricings, the labels from the orders
     * behind them; a sign without an order keeps its own entry (→ {@link CustomerorderFilterOption}).
     */
    private List<CustomerorderFilterOption> filterOptions() {
        var signs = orderPricingService.getCustomerorderSignsWithPricing();
        var ordersBySign = customerorderService.getCustomerordersBySigns(signs).stream()
            .collect(toMap(Customerorder::getSign, identity(), (first, second) -> first));
        return signs.stream()
            .map(sign -> CustomerorderFilterOption.from(sign, ordersBySign.get(sign), customerorderViewHelper))
            .toList();
    }

    /**
     * The sign a rate was stored with when the migration could not resolve its person (#968), or
     * {@code null}. The select cannot offer that person, so its empty choice names the sign instead
     * of "everyone" — saving without a person leaves such a rate as it is ({@code
     * OrderPricingService#update}).
     */
    private String unresolvedEmployeeSignOf(OrderPricingForm form) {
        if (form.isNew() || form.getEmployeeId() != null) {
            return null;
        }
        var pricing = orderPricingService.getById(form.getId());
        return pricing.isEmployeeUnresolved() ? pricing.getEmployeeSign() : null;
    }

    /**
     * The sign a rate was stored with when the migration could not resolve its order (#1212), or
     * {@code null}. The form cannot preselect that order; it names the sign so that the order to pick
     * is known.
     */
    private String unresolvedCustomerorderSignOf(OrderPricingForm form) {
        if (form.isNew() || form.getCustomerorderId() != null) {
            return null;
        }
        var pricing = orderPricingService.getById(form.getId());
        return pricing.isUnresolved() ? pricing.getCustomerorderSign() : null;
    }

    private Long customerorderIdOf(String sign) {
        if (trimToNull(sign) == null) {
            return null;
        }
        var customerorder = customerorderService.getCustomerorderBySign(sign.trim());
        return customerorder == null ? null : customerorder.getId();
    }

    private void addFormModel(Model model, OrderPricingForm form, boolean isEdit) {
        var customerorder = form.getCustomerorderId() == null
            ? null
            : customerorderService.getCustomerorderById(form.getCustomerorderId());
        model.addAttribute("pricingForm", form);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("customerorders",
            customerorderService.getSelectableCustomerorders(customerorder == null ? null : customerorder.getSign()));
        model.addAttribute("unresolvedCustomerorderSign", unresolvedCustomerorderSignOf(form));
        model.addAttribute("suborders", subordersOf(customerorder));
        model.addAttribute("employees", employeeService.getSelectableEmployees(form.getEmployeeId()));
        model.addAttribute("unresolvedEmployeeSign", unresolvedEmployeeSignOf(form));
        // The plans that can ever apply to what the form currently says — the same set the saving
        // judges by (#1065). The stored one stays in the list even once it is inactive.
        model.addAttribute("budgetPlans", orderPricingService.getSelectablePlans(
            form.getCustomerorderId(), form.getSuborderSign(), form.getValidFrom(),
            form.getValidUntil(), form.getOrderBudgetId()));
    }

    /**
     * The suborders of the selected customer order — empty while none is selected. Hidden ones are
     * left out without exception: this list only prefills the pattern, which is kept in a text field
     * of its own and therefore cannot be lost.
     */
    private List<Suborder> subordersOf(Customerorder customerorder) {
        return customerorder == null ? List.of()
            : suborderService.getSubordersByCustomerorderId(customerorder.getId());
    }

}
