package org.tb.restful.auth;

import static java.lang.Boolean.TRUE;
import static org.springframework.web.context.request.RequestAttributes.SCOPE_SESSION;
import static org.tb.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static org.tb.GlobalConstants.EMPLOYEE_STATUS_BL;
import static org.tb.GlobalConstants.EMPLOYEE_STATUS_PV;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.tb.bdom.AuthorizedUser;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;

@Slf4j
@RequiredArgsConstructor
public class AuthenticationInterceptor implements WebRequestInterceptor {

    private final AuthorizedUser authorizedUser;
    private final EmployeeDAO employeeDAO;

    @Override
    public void preHandle(WebRequest request) {
        Long employeeId = (Long) request.getAttribute("employeeId", SCOPE_SESSION);
        if(employeeId != null) {
            initAuthorizedUser(employeeDAO.getEmployeeById(employeeId));
        }
    }

    @Override
    public void postHandle(WebRequest request, ModelMap model) {
    }

    @Override
    public void afterCompletion(WebRequest request, Exception ex) {
    }

    private void initAuthorizedUser(Employee loginEmployee) {
        if(loginEmployee != null) {
            authorizedUser.setAuthenticated(true);
            authorizedUser.setEmployeeId(loginEmployee.getId());
            authorizedUser.setSign(loginEmployee.getSign());
            authorizedUser.setRestricted(TRUE.equals(loginEmployee.getRestricted()));
            boolean isManager = loginEmployee.getStatus().equals(EMPLOYEE_STATUS_BL) || loginEmployee.getStatus().equals(EMPLOYEE_STATUS_PV);
            authorizedUser.setManager(isManager);
            boolean isAdmin = loginEmployee.getStatus().equals(EMPLOYEE_STATUS_ADM);
            authorizedUser.setAdmin(isAdmin);
        } else {
            authorizedUser.setAuthenticated(false);
        }
    }

}
