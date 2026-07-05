package org.tb.budget.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.persistence.OrderBudgetRepository;
import org.tb.employee.service.EmployeeService;
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;

@Component
@RequiredArgsConstructor
public class BudgetAuthorization {

    private final AuthorizedUser authorizedUser;
    private final EmployeeService employeeService;
    private final CustomerorderService customerorderService;
    private final OrderBudgetRepository orderBudgetRepository;

    public boolean isAuthorized(OrderBudget budget) {
        if (authorizedUser.isManager()) return true;
        var responsibleSigns = getResponsibleOrderSigns();
        return responsibleSigns.contains(budget.getCustomerorderSign());
    }

    public boolean isAuthorizedForAnyBudget() {
        if (authorizedUser.isManager()) return true;
        var responsibleSigns = getResponsibleOrderSigns();
        if (responsibleSigns.isEmpty()) return false;
        return orderBudgetRepository.existsByCustomerorderSignIn(responsibleSigns);
    }

    private java.util.List<String> getResponsibleOrderSigns() {
        var employee = employeeService.getEmployeeBySign(authorizedUser.getEffectiveLoginSign());
        if (employee == null) return java.util.List.of();
        return customerorderService.getVisibleCustomerOrdersByResponsibleEmployeeId(employee.getId())
            .stream()
            .map(Customerorder::getSign)
            .toList();
    }

}
