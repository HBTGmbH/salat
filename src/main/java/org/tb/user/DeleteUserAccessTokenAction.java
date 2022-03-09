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
public class DeleteUserAccessTokenAction extends LoginRequiredAction<UserAccessTokenForm> {

  private final UserAccessTokenService userAccessTokenService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, UserAccessTokenForm form,
      HttpServletRequest request, HttpServletResponse response) throws Exception {

    String tokenIdParam = request.getParameter("id");
    if(tokenIdParam != null) {
      userAccessTokenService.delete(authorizedUser.getEmployeeId(), Long.valueOf(tokenIdParam));
    }

    request.setAttribute("passwordchanged", false);
    request.setAttribute("userAccessTokens", userAccessTokenService.getTokens(authorizedUser.getEmployeeId()));

    return mapping.findForward("success");
  }

}
