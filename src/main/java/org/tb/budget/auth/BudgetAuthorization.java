package org.tb.budget.auth;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.domain.OrderBudget;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCode;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;

/**
 * Who may see which budget data.
 *
 * <p>Managers and backoffice see every customer order. Everyone else sees exactly the orders they
 * are responsible for ({@code Customerorder#getResponsibleHbt()}) — that covers the order
 * responsibles the budget module is built for, without giving them a role. Restricted users
 * (external staff, interns) see nothing at all.
 *
 * <p>Maintaining budgets, customer rates and employee costs stays with managers; this class only
 * governs read access. The write paths carry {@code @Authorized(requiresManager = true)}.
 *
 * <p>Request scoped because the responsible orders are looked up once and then asked for repeatedly
 * — every row of a list checks them. During the alert job {@code AuthorizedUser} runs in job mode
 * and reports manager, so the lookup never happens there.
 */
@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class BudgetAuthorization {

    private final AuthorizedUser authorizedUser;
    private final AuthorizedEmployee authorizedEmployee;
    private final CustomerorderService customerorderService;

    private Set<String> responsibleSigns;

    /** Whether the user may see budget data of every customer order. */
    public boolean seesAllCustomerorders() {
        if (authorizedUser.isRestricted()) return false;
        return authorizedUser.isManager() || authorizedUser.isBackoffice();
    }

    public boolean isAuthorizedForCustomerorder(String customerorderSign) {
        if (authorizedUser.isRestricted()) return false;
        if (seesAllCustomerorders()) return true;
        return customerorderSign != null && responsibleCustomerorderSigns().contains(customerorderSign);
    }

    public boolean isAuthorized(OrderBudget budget) {
        return budget != null && isAuthorizedForCustomerorder(budget.getCustomerorderSign());
    }

    /** Whether any budget data is visible at all — drives the visibility of the budget menu. */
    public boolean isAuthorizedForAnyBudget() {
        if (authorizedUser.isRestricted()) return false;
        return seesAllCustomerorders() || !responsibleCustomerorderSigns().isEmpty();
    }

    public void checkAuthorizedForCustomerorder(String customerorderSign) {
        if (!isAuthorizedForCustomerorder(customerorderSign)) {
            throw new AuthorizationException(ErrorCode.BU_ORDER_NOT_AUTHORIZED, customerorderSign);
        }
    }

    public void checkAuthorized(OrderBudget budget) {
        checkAuthorizedForCustomerorder(budget == null ? null : budget.getCustomerorderSign());
    }

    /** The customer orders the user may pick in the budget filters. */
    public List<Customerorder> authorizedCustomerorders() {
        if (authorizedUser.isRestricted()) return List.of();
        if (seesAllCustomerorders()) return customerorderService.getAllCustomerorders();
        return responsibleCustomerorders();
    }

    /**
     * The signs of the orders the user is responsible for. Resolved once per request: an admin has
     * no employee at all, so the lookup would otherwise run on every single row.
     */
    private Set<String> responsibleCustomerorderSigns() {
        if (responsibleSigns == null) {
            responsibleSigns = responsibleCustomerorders().stream()
                .map(Customerorder::getSign)
                .collect(Collectors.toUnmodifiableSet());
        }
        return responsibleSigns;
    }

    private List<Customerorder> responsibleCustomerorders() {
        var employeeId = authorizedEmployee.getEmployeeId();
        // Admins are not employees, so they have no responsibilities — their access comes from the role.
        if (employeeId == null) return List.of();
        return customerorderService.getCustomerOrdersByResponsibleEmployeeId(employeeId);
    }

}
