package org.tb.budget.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.domain.OrderBudget;

@Component
@RequiredArgsConstructor
public class BudgetAuthorization {

    private final AuthorizedUser authorizedUser;

    public boolean isAuthorized(OrderBudget budget) {
        if (authorizedUser.isManager()) return true;
        return false;
    }

    public boolean isAuthorizedForAnyBudget() {
        if (authorizedUser.isManager()) return true;
        return false;
    }

}
