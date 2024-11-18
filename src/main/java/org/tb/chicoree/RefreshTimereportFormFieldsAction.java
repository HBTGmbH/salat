package org.tb.chicoree;

import static org.tb.common.util.DateUtils.validateDate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.order.service.EmployeeorderService;

@Component
@RequiredArgsConstructor
public class RefreshTimereportFormFieldsAction extends LoginRequiredAction<TimereportForm> {

  private final ChicoreeSessionStore chicoreeSessionStore;
  private final EmployeeorderService employeeorderService;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, TimereportForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    var event = request.getParameter("event");
    switch(event) {
      case "order-selected":
      case "date-selected":
        if(!validateDate(form.getDate())) {
          return mapping.getInputForward();
        }
        var employeecontractId = chicoreeSessionStore.getLoginEmployeecontractId().orElseThrow();
        var date = form.getDateTyped();
        var employeeorders = employeeorderService.getEmployeeordersForEmployeecontractAndValidAt(
            employeecontractId,
            date
        );
        chicoreeSessionStore.setEmployeeorders(employeeorders);
        if(form.getOrderId() != null && !form.getOrderId().isBlank()) {
          chicoreeSessionStore.setCustomerorder(form.getOrderIdTyped(), employeeorders);
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
