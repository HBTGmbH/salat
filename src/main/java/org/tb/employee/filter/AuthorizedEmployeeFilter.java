package org.tb.employee.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.filter.RequestPaths;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.service.EmployeeService;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(103)
public class AuthorizedEmployeeFilter extends OncePerRequestFilter {

    private final AuthorizedUser authorizedUser;
    private final AuthorizedEmployee authorizedEmployee;
    private final EmployeeService employeeService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (authorizedUser.isAuthenticated()) {
            var employee = employeeService.getEmployeeByLoginname(authorizedUser.getEffectiveLoginSign());
            if (employee != null) {
                authorizedEmployee.login(employee);
            } else {
                log.warn("No employee found for login sign '{}'", authorizedUser.getEffectiveLoginSign());
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return RequestPaths.isStaticResource(request);
    }
}
