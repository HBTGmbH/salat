package org.tb.common.configuration;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthorizedUser;
import org.tb.employee.persistence.EmployeeRepository;

@Slf4j
@RequiredArgsConstructor
@Component
public class AuthenticationFilter extends HttpFilter {

    private final AuthorizedUser authorizedUser;
    private final EmployeeRepository employeeRepository;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
            if (auth.getPrincipal() instanceof DefaultOAuth2User user) {
                String userSign = user.getAttribute("preferred_username");
                employeeRepository.findBySign(userSign).ifPresentOrElse(
                    authorizedUser::init,
                    () -> {
                        log.info("no user found for sign " + userSign
                            + " please contact the Administrator to create your user");
                        throw new AuthenticationCredentialsNotFoundException(
                            "no user found for sign " + userSign
                                + " please contact the Administrator to create your user");
                    });
            }

        } else {
            throw new AuthenticationServiceException("no user given from Auth-Service");
        }

        Object oldValue = request.getAttribute("authorizedUser");
        request.setAttribute("authorizedUser", authorizedUser);
        super.doFilter(request, response, chain);
        request.setAttribute("authorizedUser", oldValue);

    }

}
