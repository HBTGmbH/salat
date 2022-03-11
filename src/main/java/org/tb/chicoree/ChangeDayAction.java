package org.tb.chicoree;

import java.time.LocalDate;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.dailyreport.persistence.TimereportDAO;

@Component
@RequiredArgsConstructor
public class ChangeDayAction extends LoginRequiredAction<ActionForm> {

  private final ChicoreeSessionStore chicoreeSessionStore;
  private final TimereportDAO timereportDAO;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    String value = request.getParameter("value");
    if(value != null && !value.isBlank()) {
      var newDate = chicoreeSessionStore.getDashboardDate().orElseThrow().plusDays(Long.valueOf(value));
      var contractId = chicoreeSessionStore.getLoginEmployeecontractId().orElseThrow();
      var timereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(contractId, newDate);
      chicoreeSessionStore.setTimereports(newDate, timereports);
    }
    return mapping.findForward("success");
  }

}
