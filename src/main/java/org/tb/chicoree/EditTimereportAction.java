package org.tb.chicoree;

import static org.tb.common.util.DateUtils.parse;
import static org.tb.common.util.DateUtils.today;
import static org.tb.common.util.DateUtils.validateDate;

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
public class EditTimereportAction extends LoginRequiredAction<TimereportForm> {

  private final ChicoreeSessionStore chicoreeSessionStore;
  private final TimereportDAO timereportDAO;
  private final EmployeeorderDAO employeeorderDAO;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, TimereportForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
    String id = request.getParameter("id");
    if(id != null && !id.isBlank()) {
      // edit existing
      var timereport = timereportDAO.getTimereportById(Long.parseLong(id));
      if(timereport != null) {
        form.init(timereport);
      } else {
        return mapping.getInputForward();
      }
    } else {
      // add new
      String date = request.getParameter("date");
      if(date != null && !date.isBlank() && validateDate(date)) {
        form.initNew(parse(date), chicoreeSessionStore);
      } else {
        form.initNew(today(), chicoreeSessionStore);
      }
    }

    var employeecontractId = chicoreeSessionStore.getLoginEmployeecontractId().orElseThrow();
    var date = parse(form.getDate());
    var employeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractId(employeecontractId)
        .stream()
        .filter(employeeorder -> employeeorder.isValidAt(date))
        .collect(Collectors.toList());
    chicoreeSessionStore.setEmployeeorders(employeeorders);
    if(form.getOrderId() != null && !form.getOrderId().isBlank()) {
      // select order and prefill suborder options
      chicoreeSessionStore.setCustomerorder(form.getOrderIdTyped(), employeeorders);
    }
    return mapping.findForward("success");
  }

}
