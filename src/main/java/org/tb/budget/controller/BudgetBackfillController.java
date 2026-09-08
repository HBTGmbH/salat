package org.tb.budget.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.tb.auth.domain.Authorized;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.service.TimereportBudgetBackfillService;
import org.tb.budget.viewhelper.BudgetBackfillRowViewHelper;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;

/**
 * The one-off catch-up of the existing bookings on their budget assignment (#910). Manager only:
 * the run writes assignments across every order at once.
 */
@Controller
@RequestMapping("/budget/backfill")
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class BudgetBackfillController {

    private final TimereportBudgetBackfillService backfillService;
    private final BudgetAuthorization budgetAuthorization;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final MessageSourceAccessor messages;

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public String show(@ModelAttribute("form") BudgetBackfillForm form, Model model) {
        model.addAttribute("customerorders", budgetAuthorization.authorizedCustomerorders());
        return "budget/backfill";
    }

    /**
     * Renders the protocol straight from the POST instead of redirecting: the protocol <em>is</em>
     * the result of the write and is far too large for a flash attribute. Re-submitting therefore
     * runs again — which is safe, because a second run over the same data changes nothing.
     */
    @PostMapping("/run")
    @PreAuthorize("hasRole('MANAGER')")
    public String run(@ModelAttribute("form") BudgetBackfillForm form, Model model) {
        model.addAttribute("customerorders", budgetAuthorization.authorizedCustomerorders());
        try {
            var result = backfillService.backfill(form.getCustomerorderSign());
            model.addAttribute("result", result);
            model.addAttribute("rows", BudgetBackfillRowViewHelper.from(result));
            model.addAttribute("totals", BudgetBackfillRowViewHelper.totals(result,
                messages.getMessage("main.backfill.total", "Summe")));
        } catch (ErrorCodeException ex) {
            model.addAttribute("formErrors",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).toList());
        }
        return "budget/backfill";
    }

}
