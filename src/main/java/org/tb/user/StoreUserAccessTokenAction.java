package org.tb.user;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;

@Component
@RequiredArgsConstructor
public class StoreUserAccessTokenAction extends LoginRequiredAction<UserAccessTokenForm> {

  private final UserAccessTokenService userAccessTokenService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, UserAccessTokenForm form,
      HttpServletRequest request, HttpServletResponse response) throws Exception {

    var token = userAccessTokenService.generateToken(
        authorizedUser.getEmployeeId(), form.getParsedValidUntil(), form.getComment());

    request.setAttribute("generatedToken", token);

    return mapping.findForward("success");
  }

}
