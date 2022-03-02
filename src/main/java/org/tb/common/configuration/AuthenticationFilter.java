package org.tb.common.configuration;

import static java.lang.Boolean.TRUE;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_PV;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tb.auth.AuthorizedUser;
import org.tb.employee.Employee;
import org.tb.employee.EmployeeRepository;

@Slf4j
@RequiredArgsConstructor
public class AuthenticationFilter extends HttpFilter {

    private final AuthorizedUser authorizedUser;
    private final EmployeeRepository employeeRepository;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        if(loginEmployee != null && loginEmployee.getId() != null) {
            employeeRepository.findById(loginEmployee.getId()).ifPresent(this::initAuthorizedUser);
        }
        super.doFilter(request, response, chain);
    }

    private void initAuthorizedUser(Employee loginEmployee) {
        authorizedUser.setAuthenticated(true);
        authorizedUser.setEmployeeId(loginEmployee.getId());
        authorizedUser.setSign(loginEmployee.getSign());
        authorizedUser.setRestricted(TRUE.equals(loginEmployee.getRestricted()));
        boolean isAdmin = loginEmployee.getStatus().equals(EMPLOYEE_STATUS_ADM);
        authorizedUser.setAdmin(isAdmin);
        boolean isManager = loginEmployee.getStatus().equals(EMPLOYEE_STATUS_BL) || loginEmployee.getStatus().equals(EMPLOYEE_STATUS_PV);
        authorizedUser.setManager(isAdmin || isManager);
    }

}
