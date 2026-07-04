package org.tb.employee.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.preferences.EmployeePreferenceService;
import org.tb.employee.service.EmployeeService;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(103)
public class AuthorizedEmployeeFilter extends OncePerRequestFilter {

    private static final AntPathMatcher ANT = new AntPathMatcher();
    private static final List<String> STATIC_PATTERNS = List.of(
        "/images/**", "/webjars/**",
        "/**/*.css", "/**/*.js",
        "/**/*.gif", "/**/*.png", "/**/*.jpg", "/**/*.jpeg",
        "/**/*.svg", "/**/*.ico",
        "/**/*.woff", "/**/*.woff2", "/**/*.ttf", "/**/*.eot",
        "/**/*.map", "/**/*.webp");

    private final AuthorizedUser authorizedUser;
    private final AuthorizedEmployee authorizedEmployee;
    private final EmployeeService employeeService;
    private final EmployeePreferenceService employeePreferenceService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (authorizedUser.isAuthenticated()) {
            var employee = employeeService.getEmployeeByLoginname(authorizedUser.getEffectiveLoginSign());
            if (employee != null) {
                authorizedEmployee.login(employee);
                authorizedEmployee.setGravatarEmail(employeePreferenceService.getGravatarEmailFor(employee));
            } else {
                log.warn("No employee found for login sign '{}'", authorizedUser.getEffectiveLoginSign());
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return STATIC_PATTERNS.stream().anyMatch(p -> ANT.match(p, path));
    }
}
