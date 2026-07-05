package org.tb.budget.viewhelper;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.tb.budget.auth.BudgetAuthorization;

@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class BudgetMenuViewHelper {

    private final BudgetAuthorization budgetAuthorization;

    public boolean isBudgetMenuAvailable() {
        return budgetAuthorization.isAuthorizedForAnyBudget();
    }

}
