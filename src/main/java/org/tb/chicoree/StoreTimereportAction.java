package org.tb.chicoree;

import static org.tb.common.util.DateUtils.parse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.persistence.EmployeeorderDAO;

@Component
@RequiredArgsConstructor
public class StoreTimereportAction extends LoginRequiredAction<TimereportForm> {

  private final TimereportService timereportService;
  private final ChicoreeSessionStore chicoreeSessionStore;
  private final EmployeeorderDAO employeeorderDAO;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, TimereportForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    var employeecontractId = chicoreeSessionStore.getLoginEmployeecontractId().orElseThrow();
    var employeeorderId = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(
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
    } catch (AuthorizationException | BusinessRuleException | InvalidDataException e) {
      addToMessages(request, e.getErrorCode());
      return mapping.getInputForward();
    }
    chicoreeSessionStore.setDashboardDate(form.getDateTyped());
    return mapping.findForward("success");
  }

}
