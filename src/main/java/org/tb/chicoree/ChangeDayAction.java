package org.tb.chicoree;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.LoginRequiredAction;

@Component
@RequiredArgsConstructor
public class ChangeDayAction extends LoginRequiredAction<ActionForm> {

  private final ChicoreeSessionStore chicoreeSessionStore;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    String value = request.getParameter("value");
    if(value != null && !value.isBlank()) {
      var newDate = chicoreeSessionStore.getDashboardDate().orElseThrow().plusDays(Long.parseLong(value));
      chicoreeSessionStore.setDashboardDate(newDate);
    }
    return mapping.findForward("success");
  }

}
