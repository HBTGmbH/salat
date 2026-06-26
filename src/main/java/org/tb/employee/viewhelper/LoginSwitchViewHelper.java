package org.tb.employee.viewhelper;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;

@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class LoginSwitchViewHelper {

    private final EmployeeService employeeService;
    private final AuthorizedEmployee authorizedEmployee;
    private final AuthorizedUser authorizedUser;

    private List<Employee> cachedEmployees;

    private List<Employee> getLoginEmployees() {
        if (cachedEmployees == null) {
            cachedEmployees = employeeService.getLoginEmployees();
        }
        return cachedEmployees;
    }

    public List<Employee> getSwitchableLoginEmployees() {
        String effectiveSign = authorizedUser.getEffectiveLoginSign();
        return getLoginEmployees().stream()
            .filter(emp -> !emp.getLoginname().equalsIgnoreCase(effectiveSign))
            .toList();
    }

    public boolean isVisible() {
        return !getSwitchableLoginEmployees().isEmpty();
    }

}
