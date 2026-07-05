package org.tb.budget.controller;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.domain.OrderPricingData;
import org.tb.budget.service.OrderPricingService;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.employee.service.EmployeeService;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

@Controller
@RequestMapping("/budget/pricing")
@RequiredArgsConstructor
@Authorized
public class OrderPricingController {

    private final OrderPricingService orderPricingService;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final EmployeeService employeeService;
    private final AuthorizedUser authorizedUser;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final MessageSourceAccessor messages;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pricings", orderPricingService.getAll());
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
            blankToNull(form.getSuborderSign()),
            blankToNull(form.getEmployeeSign()),
            blankToNull(form.getDescription()),
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

    private void addFormModel(Model model, OrderPricingForm form, boolean isEdit) {
        model.addAttribute("pricingForm", form);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("customerorders", customerorderService.getAllCustomerorders());
        model.addAttribute("suborders", suborderService.getAllSuborders());
        model.addAttribute("employees", employeeService.getAllEmployees());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

}
