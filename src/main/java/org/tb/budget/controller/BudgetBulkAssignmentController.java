package org.tb.budget.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.Authorized;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.domain.BulkAssignmentEmployee;
import org.tb.budget.service.OrderBudgetService;
import org.tb.budget.service.TimereportBudgetBulkAssignmentService;
import org.tb.budget.viewhelper.BulkAssignmentPreviewViewHelper;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.util.DurationUtils;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Assigning the bookings of a period to one budget plan (#911). Manager only — the run moves
 * bookings between plans and thereby changes what the controlling reports.
 */
@Controller
@RequestMapping("/budget/bulk-assignment")
@RequiredArgsConstructor
@Authorized(requiresManager = true)
@PreAuthorize("hasRole('MANAGER')")
public class BudgetBulkAssignmentController {

    private final TimereportBudgetBulkAssignmentService bulkAssignmentService;
    private final OrderBudgetService orderBudgetService;
    private final BudgetAuthorization budgetAuthorization;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final MessageSourceAccessor messages;

    @GetMapping
    public String show(@ModelAttribute("form") BulkAssignmentForm form, Model model) {
        addSelectionLists(form, model);
        return "budget/bulk-assignment";
    }

    /**
     * The preview, delivered as an out-of-band swap of the preview panel. The selection fields post
     * here on change, so the numbers are never stale relative to the fields they describe.
     */
    @PostMapping("/preview")
    public String preview(@ModelAttribute("form") BulkAssignmentForm form, Model model,
                          HttpServletRequest request) {
        addSelectionLists(form, model);
        model.addAttribute("htmxRequest", "true".equals(request.getHeader("HX-Request")));
        addPreview(form, model);
        return "budget/bulk-assignment";
    }

    /**
     * The order drives all three dependent selects, so changing it replaces them as well. The preview
     * panel comes along and is empty until a plan is picked again — a preview belonging to the
     * previous order would be worse than none.
     */
    @PostMapping("/refresh")
    public String refresh(@ModelAttribute("form") BulkAssignmentForm form, Model model,
                          HttpServletRequest request) {
        form.setSuborderSign(null);
        form.setTargetBudgetId(null);
        form.setEmployeeIds(new ArrayList<>());
        addSelectionLists(form, model);
        model.addAttribute("htmxRequest", "true".equals(request.getHeader("HX-Request")));
        model.addAttribute("selectionChanged", true);
        model.addAttribute("employeesChanged", true);
        return "budget/bulk-assignment";
    }

    /**
     * Suborder and period narrow the bookings the people are derived from, so changing them rebuilds
     * that select — and drops anyone who no longer books in the selection (#953). Suborder and plan
     * stay as they are: they do not depend on each other, only on the order.
     */
    @PostMapping("/refresh-employees")
    public String refreshEmployees(@ModelAttribute("form") BulkAssignmentForm form, Model model,
                                   HttpServletRequest request) {
        form.retainEmployees(addSelectionLists(form, model));
        model.addAttribute("htmxRequest", "true".equals(request.getHeader("HX-Request")));
        model.addAttribute("employeesChanged", true);
        addPreview(form, model);
        return "budget/bulk-assignment";
    }

    @PostMapping("/assign")
    public String assign(@ModelAttribute("form") BulkAssignmentForm form, Model model,
                         RedirectAttributes redirectAttributes) {
        if (!form.isComplete()) {
            addSelectionLists(form, model);
            model.addAttribute("formErrors",
                List.of(messages.getMessage("main.bulkassignment.error.incomplete")));
            return "budget/bulk-assignment";
        }
        try {
            var written = bulkAssignmentService.assign(form.toData());
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.bulkassignment.message.done",
                    new Object[] {written.bookings(), DurationUtils.format(written.hours())}));
            return "redirect:/budget/bulk-assignment";
        } catch (ErrorCodeException ex) {
            addSelectionLists(form, model);
            model.addAttribute("formErrors", toMessages(ex));
            return "budget/bulk-assignment";
        }
    }

    /**
     * The orders the manager may see, plus the suborders, plans and people of the selected order.
     * Returns the people so a caller that has to prune the choice does not query them twice.
     */
    private List<BulkAssignmentEmployee> addSelectionLists(BulkAssignmentForm form, Model model) {
        model.addAttribute("customerorders", budgetAuthorization.authorizedCustomerorders());
        var sign = form.getCustomerorderSign();
        if (sign == null || sign.isBlank()) {
            model.addAttribute("suborders", List.of());
            model.addAttribute("budgets", List.of());
            model.addAttribute("employees", List.of());
            return List.of();
        }
        var customerorder = customerorderService.getCustomerorderBySign(sign);
        model.addAttribute("suborders", customerorder == null
            ? List.of()
            : suborderService.getSubordersByCustomerorderId(customerorder.getId()));
        // Only active plans can hold bookings, so offering the inactive ones would only produce a
        // preview in which everything is unassignable.
        model.addAttribute("budgets", orderBudgetService.getActiveByCustomerorderSign(sign));
        // Only people who actually booked in the current selection (#953) — the list is derived from
        // the bookings themselves, so no choice can come up empty.
        var employees = bulkAssignmentService.selectableEmployees(form.toData());
        model.addAttribute("employees", employees);
        return employees;
    }

    private void addPreview(BulkAssignmentForm form, Model model) {
        if (!form.isComplete()) {
            return;
        }
        try {
            model.addAttribute("preview", BulkAssignmentPreviewViewHelper.from(
                bulkAssignmentService.preview(form.toData()), form.isIncludeAssigned()));
        } catch (ErrorCodeException ex) {
            model.addAttribute("formErrors", toMessages(ex));
        }
    }

    private List<String> toMessages(ErrorCodeException ex) {
        return errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).toList();
    }

}
