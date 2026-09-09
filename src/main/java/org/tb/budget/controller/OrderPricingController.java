package org.tb.budget.controller;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.trimToNull;

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
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.domain.OrderPricingData;
import org.tb.budget.service.OrderPricingService;
import org.tb.budget.viewhelper.OrderPricingFilterOption;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.employee.service.EmployeeService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;
import org.tb.order.viewhelper.CustomerorderViewHelper;

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
    private final MessageSourceAccessor messages;

    /**
     * The parameters are prefixed rather than plain {@code showInactive} / {@code showExpiredOrders}
     * because the UiState mapping is global: the plan list has a switch of the same name that means
     * something else, and both would otherwise share one remembered value (#952).
     *
     * <p>The two switches are independent (#957): one is about the validity of the rate, the other
     * about the validity of its order. A rate can have expired while its order runs on, and a
     * current rate can hang off an order that ended last year.
     */
    @GetMapping
    public String list(@RequestParam(required = false) String coSign,
                       @RequestParam(required = false) Boolean pricingShowInactive,
                       @RequestParam(required = false) Boolean pricingShowExpiredOrders,
                       Model model) {
        var inactive = Boolean.TRUE.equals(pricingShowInactive);
        var expiredOrders = Boolean.TRUE.equals(pricingShowExpiredOrders);
        // The rows name their order by sign; description, customer and validity hang off the order.
        model.addAttribute("rows", orderPricingService.getRows(coSign, inactive, expiredOrders));
        model.addAttribute("customerorderOptions", filterOptions());
        model.addAttribute("coSign", coSign);
        model.addAttribute("showInactive", inactive);
        model.addAttribute("showExpiredOrders", expiredOrders);
        model.addAttribute("isManager", authorizedUser.isManager());
        return "budget/pricing-list";
    }

    @Authorized(requiresManager = true)
    @GetMapping("/create")
    public String createForm(Model model) {
        addFormModel(model, new OrderPricingForm(), false);
        return "budget/pricing-form";
    }

    @Authorized(requiresManager = true)
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        var pricing = orderPricingService.getById(id);
        var form = new OrderPricingForm();
        form.setId(pricing.getId());
        form.setCustomerorderSign(pricing.getCustomerorderSign());
        form.setSuborderSign(pricing.getSuborderSign());
        form.setEmployeeSign(pricing.getEmployeeSign());
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
        if (form.getCustomerorderSign() == null || form.getCustomerorderSign().isBlank()) {
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
            form.getCustomerorderSign(),
            trimToNull(form.getSuborderSign()),
            trimToNull(form.getEmployeeSign()),
            trimToNull(form.getDescription()),
            form.getPriceEuro().movePointRight(2).intValue(),
            form.getValidFrom(),
            form.getValidUntil()
        );

        try {
            if (form.isNew()) {
                orderPricingService.save(data);
                redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.pricing.message.created"));
            } else {
                orderPricingService.update(form.getId(), data);
                redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.pricing.message.updated"));
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
     * Refills the suborder picker when the customer order changes. The picker only prefills the
     * pattern, but offering the suborders of every order would make it useless — and it is a list of
     * several thousand entries.
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
     * behind them; a sign without an order keeps its own entry (→ {@link OrderPricingFilterOption}).
     */
    private List<OrderPricingFilterOption> filterOptions() {
        var signs = orderPricingService.getCustomerorderSignsWithPricing();
        var ordersBySign = customerorderService.getCustomerordersBySigns(signs).stream()
            .collect(toMap(Customerorder::getSign, identity(), (first, second) -> first));
        return signs.stream()
            .map(sign -> OrderPricingFilterOption.from(sign, ordersBySign.get(sign), customerorderViewHelper))
            .toList();
    }

    private void addFormModel(Model model, OrderPricingForm form, boolean isEdit) {
        model.addAttribute("pricingForm", form);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("customerorders",
            customerorderService.getSelectableCustomerorders(form.getCustomerorderSign()));
        model.addAttribute("suborders", subordersOf(form.getCustomerorderSign()));
        model.addAttribute("employees", employeeService.getSelectableEmployees(form.getEmployeeSign()));
    }

    /**
     * The suborders of the selected customer order — empty while none is selected. Hidden ones are
     * left out without exception: this list only prefills the pattern, which is kept in a text field
     * of its own and therefore cannot be lost.
     */
    private List<Suborder> subordersOf(String customerorderSign) {
        if (trimToNull(customerorderSign) == null) {
            return List.of();
        }
        var customerorder = customerorderService.getCustomerorderBySign(customerorderSign);
        return customerorder == null ? List.of()
            : suborderService.getSubordersByCustomerorderId(customerorder.getId());
    }

}
