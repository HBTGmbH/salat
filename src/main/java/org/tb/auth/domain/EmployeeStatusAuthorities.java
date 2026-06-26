package org.tb.auth.domain;

import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BL;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_BO;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_PV;
import static org.tb.common.GlobalConstants.EMPLOYEE_STATUS_RESTRICTED;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class EmployeeStatusAuthorities {

    public static Collection<GrantedAuthority> from(String status) {
        boolean isRestricted = EMPLOYEE_STATUS_RESTRICTED.equalsIgnoreCase(status);
        boolean isAdmin      = EMPLOYEE_STATUS_ADM.equalsIgnoreCase(status);
        boolean isManager    = isAdmin || EMPLOYEE_STATUS_BL.equalsIgnoreCase(status);
        boolean isPeopleLead = isManager || EMPLOYEE_STATUS_PV.equalsIgnoreCase(status);
        boolean isBackoffice = isManager || EMPLOYEE_STATUS_BO.equalsIgnoreCase(status);
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (isRestricted) authorities.add(new SimpleGrantedAuthority("ROLE_RESTRICTED"));
        if (isAdmin)      authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        if (isManager)    authorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
        if (isPeopleLead) authorities.add(new SimpleGrantedAuthority("ROLE_PEOPLE_LEAD"));
        if (isBackoffice) authorities.add(new SimpleGrantedAuthority("ROLE_BACKOFFICE"));
        return authorities;
    }
}
