package org.tb.common.filter;

import java.io.IOException;
import java.security.Principal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.tb.auth.AuthViewHelper;
import org.tb.auth.AuthorizedUser;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;


@Slf4j
@RequiredArgsConstructor
public class AuthenticationFilter extends HttpFilter {

    private final AuthViewHelper authViewHelper;
    private final AuthorizedUser authorizedUser;
    private final EmployeeRepository employeeRepository;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        Principal principal = SecurityContextHolder.getContext().getAuthentication();
        if(principal != null && principal.getName() != null) {
            authorizedUser.setLoginSign(principal.getName());
            if(request.getSession(false) != null && request.getSession().getAttribute("loginEmployee") != null) {
                authorizedUser.init((Employee) request.getSession().getAttribute("loginEmployee"));
            } else {
                employeeRepository.findBySign(principal.getName()).ifPresent(authorizedUser::init);
            }
        }
        Object oldValue = request.getAttribute("authorizedUser");
        request.setAttribute("authorizedUser", authorizedUser);
        request.setAttribute("authViewHelper", authViewHelper);
        super.doFilter(request, response, chain);
        request.setAttribute("authorizedUser", oldValue);

    }

}
