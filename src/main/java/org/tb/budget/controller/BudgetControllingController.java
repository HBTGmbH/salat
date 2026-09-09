package org.tb.budget.controller;

import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.tb.budget.controller.BudgetUiStateKeyContributor.CUSTOMER_ORDER_SIGN;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.auth.BudgetAuthorization;
import org.tb.budget.service.BudgetControllingService;
import org.tb.common.web.UiState;

@Controller
@RequestMapping("/budget/controlling")
@RequiredArgsConstructor
@Authorized(requireUnrestricted = true)
public class BudgetControllingController {

    private final BudgetControllingService budgetControllingService;
    private final BudgetAuthorization budgetAuthorization;
    private final AuthorizedUser authorizedUser;
    private final UiState uiState;

    /**
     * The chosen order is shared with the plan and rate lists (#952). It is read from and written to
     * the UiState here rather than through a registered parameter mapping: the parameter name
     * {@code customerorderSign} is a form field of five other budget forms, and a global mapping
     * would feed them the remembered order as a fallback value.
     *
     * <p>A remembered order is only preselected, never evaluated. Without a period an evaluation
     * spans 2000 to 2999 — the most expensive one the module has — and merely navigating to the page
     * must not trigger it. The evaluation stays bound to the submitted form, which is also what the
     * links from the dashboard and from the budget alert mails do.
     */
    @GetMapping
    public String show(@ModelAttribute("filter") ControllingFilterForm filter,
                       HttpServletRequest request,
                       Model model) {
        model.addAttribute("customerorders", budgetAuthorization.authorizedCustomerorders());
        model.addAttribute("isManager", authorizedUser.isManager());

        var submitted = request.getParameterMap().containsKey("customerorderSign");
        var sign = trimToNull(filter.getCustomerorderSign());
        if (submitted) {
            // An explicit choice wins and travels to the other lists — the empty one included, so
            // that clearing the filter here clears it everywhere.
            uiState.setValue(CUSTOMER_ORDER_SIGN, sign == null ? "" : sign);
        } else {
            filter.setCustomerorderSign(trimToNull(uiState.getValue(CUSTOMER_ORDER_SIGN)));
        }

        if (submitted && sign != null) {
            // Deliberately outside the try below: a missing privilege must not degrade into a hint
            // next to an empty evaluation.
            budgetAuthorization.checkAuthorizedForCustomerorder(sign);
            var from = filter.getFrom() != null ? filter.getFrom() : LocalDate.of(2000, 1, 1);
            var until = filter.getUntil() != null ? filter.getUntil() : LocalDate.of(2999, 12, 31);
            try {
                var result = budgetControllingService.compute(sign, from, until, authorizedUser.isManager());
                model.addAttribute("result", result);
            } catch (Exception ex) {
                model.addAttribute("controllingError", ex.getMessage());
            }
        }

        return "budget/controlling";
    }
}
