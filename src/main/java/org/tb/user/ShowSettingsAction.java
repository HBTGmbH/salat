package org.tb.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.LoginRequiredAction;

@Component
@RequiredArgsConstructor
public class ShowSettingsAction extends LoginRequiredAction<ShowSettingsForm> {

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping,
        ShowSettingsForm settingsForm, HttpServletRequest request,
        HttpServletResponse response) throws Exception {

        return mapping.findForward("success");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }

}
