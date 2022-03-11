package org.tb.chicoree;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;

@Component
@RequiredArgsConstructor
public class StoreTimereportAction extends LoginRequiredAction<TimereportForm> {

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, TimereportForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    return mapping.findForward("success");
  }

}
