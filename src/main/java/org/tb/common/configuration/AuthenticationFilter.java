package org.tb.common.configuration;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tb.auth.AuthorizedUser;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;

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
            employeeRepository.findById(loginEmployee.getId()).ifPresent(authorizedUser::init);
        }
        Object oldValue = request.getAttribute("authorizedUser");
        request.setAttribute("authorizedUser", authorizedUser);
        super.doFilter(request, response, chain);
        request.setAttribute("authorizedUser", oldValue);

    }

}