package org.tb.user;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.employee.persistence.EmployeeDAO;

@Component
@RequiredArgsConstructor
public class ShowSettingsAction extends LoginRequiredAction<ShowSettingsForm> {

    private final EmployeeDAO employeeDAO;
    private final UserAccessTokenService userAccessTokenService;
    private final UserService userService;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping,
        ShowSettingsForm settingsForm, HttpServletRequest request,
        HttpServletResponse response) throws Exception {

        request.setAttribute("userAccessTokens", userAccessTokenService.getTokens(authorizedUser.getEmployeeId()));
        return mapping.findForward("success");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }

}
