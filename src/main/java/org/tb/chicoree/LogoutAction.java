package org.tb.chicoree;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthorizedUser;
import org.tb.common.struts.LoginRequiredAction;import org.tb.common.struts.TypedAction;

@Component
@RequiredArgsConstructor
public class LogoutAction extends LoginRequiredAction<ActionForm> {

  private final AuthorizedUser authorizedUser;
  private final ChicoreeSessionStore chicoreeSessionStore;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    chicoreeSessionStore.invalidate();
    authorizedUser.invalidate();
    return mapping.findForward("success");
  }

}
