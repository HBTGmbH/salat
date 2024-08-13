package org.tb.common.filter;

import java.io.IOException;
import java.security.Principal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.tb.auth.AuthViewHelper;
import org.tb.auth.AuthorizedUser;
import org.tb.auth.UserRole;
import org.tb.auth.UserRoleRepository;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;


@Slf4j
@RequiredArgsConstructor
public class AuthenticationFilter extends HttpFilter {

    private final AuthViewHelper authViewHelper;
    private final AuthorizedUser authorizedUser;
    private final EmployeeRepository employeeRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        Principal principal = SecurityContextHolder.getContext().getAuthentication();
        if(principal != null && principal.getName() != null) {
            authorizedUser.setLoginSign(principal.getName());
            var loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
            if(request.getSession(false) != null && loginEmployee != null) {
                var loginEmployeeRoles = (Set<UserRole>) request.getSession().getAttribute("loginEmployeeRoles");
                authorizedUser.init(loginEmployee, loginEmployeeRoles);
            } else {
                employeeRepository.findBySign(principal.getName()).ifPresent(e -> {
                    var roles = userRoleRepository.findAllByUserId(e.getSign());
                    authorizedUser.init(e, roles);
                });
            }
        }
        Object oldValue = request.getAttribute("authorizedUser");
        request.setAttribute("authorizedUser", authorizedUser);
        request.setAttribute("authViewHelper", authViewHelper);
        super.doFilter(request, response, chain);
        request.setAttribute("authorizedUser", oldValue);

    }

}
