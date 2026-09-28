package de.hbt.salat.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import de.hbt.salat.auth.domain.AuthUiStateKeyContributor;
import de.hbt.salat.auth.domain.EmployeeStatusAuthorities;
import de.hbt.salat.auth.domain.ImpersonatedAuthentication;
import de.hbt.salat.common.web.UiState;

@Component
@RequiredArgsConstructor
public class ImpersonationSecurityInterceptor implements HandlerInterceptor {

    private final UiState uiState;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        Authentication base = unwrapped();
        if (base == null) return true;

        String impStatus = uiState.getValue(AuthUiStateKeyContributor.IMPERSONATE_LOGIN_STATUS);
        if (impStatus != null) {
            SecurityContextHolder.getContext().setAuthentication(
                new ImpersonatedAuthentication(base, EmployeeStatusAuthorities.from(impStatus))
            );
        }
        return true;
    }

    private Authentication unwrapped() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth instanceof ImpersonatedAuthentication imp ? imp.getOriginal() : auth;
    }
}
