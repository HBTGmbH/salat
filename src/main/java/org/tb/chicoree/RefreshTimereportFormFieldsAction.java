package org.tb.chicoree;

import static org.tb.common.util.DateUtils.parse;

import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.order.persistence.EmployeeorderDAO;

@Component
@RequiredArgsConstructor
public class RefreshTimereportFormFieldsAction extends LoginRequiredAction<TimereportForm> {

  private final ChicoreeSessionStore chicoreeSessionStore;
  private final TimereportDAO timereportDAO;
  private final EmployeeorderDAO employeeorderDAO;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, TimereportForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    var event = request.getParameter("event");
    switch(event) {
      case "order-selected":
      case "date-selected":
        var employeecontractId = chicoreeSessionStore.getLoginEmployeecontractId().orElseThrow();
        var date = parse(form.getDate());
        var employeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractId(employeecontractId)
            .stream()
            .filter(employeeorder -> employeeorder.isValidAt(date))
            .collect(Collectors.toList());
        chicoreeSessionStore.setEmployeeorders(employeeorders);
        if(form.getOrderId() != null && !form.getOrderId().isBlank()) {
          chicoreeSessionStore.setCustomerorder(Long.parseLong(form.getOrderId()), employeeorders);
        }
        // check if selected orderId and suborderId still viable options, if not set them to null
        var matched = chicoreeSessionStore.getOrderOptions()
            .stream()
            .filter(o -> o.getValue().equals(form.getOrderId()))
            .count();
        if(matched == 0) {
          form.setOrderId("");
        }
        break;
      default:
        throw new RuntimeException("Unsupported event: " + event);
    }
    return mapping.findForward("success");
  }

}
