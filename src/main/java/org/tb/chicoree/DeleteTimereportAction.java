package org.tb.chicoree;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.common.exception.ErrorCodeException;
import org.tb.dailyreport.service.TimereportService;

@Component
@RequiredArgsConstructor
public class DeleteTimereportAction extends LoginRequiredAction<ActionForm> {

  private final TimereportService timereportService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    try {
      String id = request.getParameter("id");
      if(id != null && !id.isBlank()) {
        timereportService.deleteTimereport(Long.parseLong(id), authorizedUser);
      }
      return mapping.findForward("success");
    } catch (ErrorCodeException e) {
      addToErrors(request, e);
      return mapping.getInputForward();
    }
  }

}
