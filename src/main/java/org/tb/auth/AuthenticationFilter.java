package org.tb.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.tb.auth.service.AuthService;


@Slf4j
@RequiredArgsConstructor
public class AuthenticationFilter extends HttpFilter {

    private final AuthViewHelper authViewHelper;
    private final AuthorizedUser authorizedUser;
    private final AuthService authService;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        Principal principal = SecurityContextHolder.getContext().getAuthentication();
        if(principal != null && principal.getName() != null) {
            authorizedUser.setLoginSign(principal.getName());
            authService.initAuthorizedUser(principal, authorizedUser);
        }
        Object oldValue = request.getAttribute("authorizedUser");
        request.setAttribute("authorizedUser", authorizedUser);
        request.setAttribute("authViewHelper", authViewHelper);
        super.doFilter(request, response, chain);
        request.setAttribute("authorizedUser", oldValue);

    }

}
