package org.tb.chicoree;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.chicoree.ChicoreeSessionStore.TimereportData;
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

    if(form.isNew()) {
      timereportService.createTimereports(
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
    chicoreeSessionStore.setLastStoredTimereport(new TimereportData(form.getOrderId(), form.getSuborderId()));
    chicoreeSessionStore.setDashboardDate(form.getDateTyped());
    return mapping.findForward("success");
  }

}
