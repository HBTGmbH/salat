package org.tb.budget.controller;

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
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.OrderBudgetAdjustmentData;
import org.tb.budget.domain.OrderBudgetData;
import org.tb.budget.domain.OrderBudgetScopeEntryData;
import org.tb.budget.domain.ProgressMode;
import org.tb.budget.service.OrderBudgetService;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

@Controller
@RequestMapping("/budget")
@RequiredArgsConstructor
@Authorized
public class BudgetController {

    private final OrderBudgetService orderBudgetService;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final AuthorizedUser authorizedUser;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final MessageSourceAccessor messages;

    @GetMapping
    public String list(@RequestParam(required = false) String coSign,
                       @RequestParam(required = false) Boolean showInactive,
                       Model model) {
        List<OrderBudget> budgets;
        if (coSign != null && !coSign.isBlank()) {
            budgets = Boolean.TRUE.equals(showInactive)
                ? orderBudgetService.getByCustomerorderSign(coSign)
                : orderBudgetService.getActiveByCustomerorderSign(coSign);
        } else {
            budgets = orderBudgetService.getAll();
            if (!Boolean.TRUE.equals(showInactive)) {
                budgets = budgets.stream().filter(b -> Boolean.TRUE.equals(b.getActive())).toList();
            }
        }
        model.addAttribute("budgets", budgets);
        model.addAttribute("coSign", coSign);
        model.addAttribute("showInactive", Boolean.TRUE.equals(showInactive));
        model.addAttribute("isManager", authorizedUser.isManager());
        model.addAttribute("customerorders", customerorderService.getAllCustomerorders());
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
            form.getSuborderSign(),
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
    public String detail(@PathVariable long id, Model model) {
        var budget = orderBudgetService.getById(id);
        model.addAttribute("budget", budget);
        model.addAttribute("adjustmentForm", new OrderBudgetAdjustmentForm());
        model.addAttribute("scopeEntryForm", new OrderBudgetScopeEntryForm());
        model.addAttribute("progressModes", ProgressMode.values());
        model.addAttribute("isManager", authorizedUser.isManager());
        return "budget/budget-detail";
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

    private void addFormModel(Model model, OrderBudgetForm form, boolean isEdit) {
        model.addAttribute("budgetForm", form);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("customerorders", customerorderService.getAllCustomerorders());
        model.addAttribute("suborders", suborderService.getAllSuborders());
        model.addAttribute("progressModes", ProgressMode.values());
    }

}
