package org.tb.auth.domain;

import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BO;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_MA;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_PV;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_RESTRICTED;

import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.tb.common.web.LoginSignProvider;
import org.tb.common.web.UiState;

@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AuthorizedUser implements LoginSignProvider {

    private final UiState uiState;

    // Set only during scheduled job execution (no HTTP request / no SecurityContext available).
    private boolean jobMode = false;
    private boolean jobAuthenticated;
    private String jobLoginSign;
    private String jobLoginStatus;
    private boolean jobRestricted;
    private boolean jobAdmin;
    private boolean jobManager;
    private boolean jobPeopleLead;
    private boolean jobBackoffice;

    public AuthorizedUser(UiState uiState) {
        this.uiState = uiState;
    }

    public void initForJob() {
        jobMode = true;
        jobAuthenticated = true;
        jobLoginSign = "SYSTEM";
        jobLoginStatus = "job";
        jobRestricted = false;
        jobAdmin = false;
        jobManager = true;
        jobPeopleLead = true;
        jobBackoffice = true;
    }

    public boolean isAuthenticated() {
        if (jobMode) return jobAuthenticated;
        Authentication auth = getAuth();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    public String getLoginSign() {
        if (jobMode) return jobLoginSign;
        Authentication auth = getAuth();
        return auth != null ? auth.getName() : null;
    }

    public String getImpersonateLoginSign() {
        if (jobMode) return null;
        return uiState.getValue(AuthUiStateKeyContributor.IMPERSONATE_LOGIN_SIGN);
    }

    @Override
    public String getEffectiveLoginSign() {
        String impersonate = getImpersonateLoginSign();
        return impersonate != null ? impersonate : getLoginSign();
    }

    public String getLoginStatus() {
        if (jobMode) return jobLoginStatus;
        if (hasAuthority("ROLE_ADMIN")) return EMPLOYEE_STATUS_ADM;
        if (hasAuthority("ROLE_MANAGER")) return EMPLOYEE_STATUS_BL;
        if (hasAuthority("ROLE_PEOPLE_LEAD")) return EMPLOYEE_STATUS_PV;
        if (hasAuthority("ROLE_BACKOFFICE")) return EMPLOYEE_STATUS_BO;
        if (hasAuthority("ROLE_RESTRICTED")) return EMPLOYEE_STATUS_RESTRICTED;
        return EMPLOYEE_STATUS_MA;
    }

    public boolean isRestricted() {
        if (jobMode) return jobRestricted;
        return hasAuthority("ROLE_RESTRICTED");
    }

    public boolean isAdmin() {
        if (jobMode) return jobAdmin;
        return hasAuthority("ROLE_ADMIN");
    }

    public boolean isManager() {
        if (jobMode) return jobManager;
        return hasAuthority("ROLE_MANAGER");
    }

    public boolean isPeopleLead() {
        if (jobMode) return jobPeopleLead;
        return hasAuthority("ROLE_PEOPLE_LEAD");
    }

    public boolean isBackoffice() {
        if (jobMode) return jobBackoffice;
        return hasAuthority("ROLE_BACKOFFICE");
    }

    private Authentication getAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean hasAuthority(String role) {
        Authentication auth = getAuth();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(ga -> role.equals(ga.getAuthority()));
    }
}
