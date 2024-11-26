package org.tb.chicoree;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.chicoree.ChicoreeSessionStore.TimereportData;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.InvalidDataException;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.service.EmployeeorderService;

@Component
@RequiredArgsConstructor
public class StoreTimereportAction extends LoginRequiredAction<TimereportForm> {

  private final TimereportService timereportService;
  private final ChicoreeSessionStore chicoreeSessionStore;
  private final EmployeeorderService employeeorderService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, TimereportForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    var employeecontractId = chicoreeSessionStore.getLoginEmployeecontractId().orElseThrow();
    var employeeorderId = employeeorderService.getEmployeeorderForEmployeecontractValidAt(
        employeecontractId,
        form.getSuborderIdTyped(),
        form.getDateTyped()
    ).getId();
    try {
      if(form.isNew()) {
        timereportService.createTimereports(
            authorizedUser,
            employeecontractId,
            employeeorderId,
            form.getDateTyped(),
            form.getComment(),
            false,
            form.getHoursTyped(),
            form.getMinutesTyped(),
            1
        );
      } else {
        timereportService.updateTimereport(
            authorizedUser,
            form.getIdTyped(),
            employeecontractId,
            employeeorderId,
            form.getDateTyped(),
            form.getComment(),
            false,
            form.getHoursTyped(),
            form.getMinutesTyped()
        );
      }
    } catch (ErrorCodeException e) {
      addToErrors(request, e);
      return mapping.getInputForward();
    }
    chicoreeSessionStore.setLastStoredTimereport(new TimereportData(form.getOrderId(), form.getSuborderId()));
    chicoreeSessionStore.setDashboardDate(form.getDateTyped());
    return mapping.findForward("success");
  }

}
