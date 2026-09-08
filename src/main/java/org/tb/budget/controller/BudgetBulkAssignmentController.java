package org.tb.budget.controller;

import jakarta.servlet.http.HttpServletRequest;
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
        if (form.isComplete()) {
            try {
                model.addAttribute("preview", BulkAssignmentPreviewViewHelper.from(
                    bulkAssignmentService.preview(form.toData()), form.isIncludeAssigned()));
            } catch (ErrorCodeException ex) {
                model.addAttribute("formErrors", toMessages(ex));
            }
        }
        return "budget/bulk-assignment";
    }

    /**
     * The order drives both dependent selects, so changing it replaces them as well. The preview
     * panel comes along and is empty until a plan is picked again — a preview belonging to the
     * previous order would be worse than none.
     */
    @PostMapping("/refresh")
    public String refresh(@ModelAttribute("form") BulkAssignmentForm form, Model model,
                          HttpServletRequest request) {
        form.setSuborderSign(null);
        form.setTargetBudgetId(null);
        addSelectionLists(form, model);
        model.addAttribute("htmxRequest", "true".equals(request.getHeader("HX-Request")));
        model.addAttribute("selectionChanged", true);
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

    /** The orders the manager may see, plus the suborders and plans of the selected order. */
    private void addSelectionLists(BulkAssignmentForm form, Model model) {
        model.addAttribute("customerorders", budgetAuthorization.authorizedCustomerorders());
        var sign = form.getCustomerorderSign();
        if (sign == null || sign.isBlank()) {
            model.addAttribute("suborders", List.of());
            model.addAttribute("budgets", List.of());
            return;
        }
        var customerorder = customerorderService.getCustomerorderBySign(sign);
        model.addAttribute("suborders", customerorder == null
            ? List.of()
            : suborderService.getSubordersByCustomerorderId(customerorder.getId()));
        // Only active plans can hold bookings, so offering the inactive ones would only produce a
        // preview in which everything is unassignable.
        model.addAttribute("budgets", orderBudgetService.getActiveByCustomerorderSign(sign));
    }

    private List<String> toMessages(ErrorCodeException ex) {
        return errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).toList();
    }

}
