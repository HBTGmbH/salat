package org.tb.chicoree;

import static org.apache.struts.action.ActionMessages.GLOBAL_MESSAGE;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthService;
import org.tb.auth.AuthorizedUser;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.common.struts.TypedAction;
import org.tb.employee.service.EmployeecontractService;

@Component
@RequiredArgsConstructor
public class LoginAction extends TypedAction<LoginForm> {

  private final AuthService authService;
  private final AuthorizedUser authorizedUser;
  private final ChicoreeSessionStore chicoreeSessionStore;
  private final EmployeecontractService employeecontractService;

  @Override
  public ActionForward executeWithForm(ActionMapping mapping, LoginForm form, HttpServletRequest request,
      HttpServletResponse response) {
    return authService.authenticate(form.getLoginname(), form.getPassword())
        .map(employee -> {
          return employeecontractService.getCurrentContract(employee.getId())
              .map(employeecontract -> {
                authorizedUser.init(employee);
                chicoreeSessionStore.setLoginEmployee(employee);
                chicoreeSessionStore.setLoginEmployeecontractId(employeecontract.getId());
                return mapping.findForward("success");
              }).orElseGet(() -> {
                ActionMessages messages = new ActionMessages();
                messages.add(GLOBAL_MESSAGE, new ActionMessage("form.login.error.invalidcontract"));
                saveMessages(request, messages);
                return mapping.getInputForward();
              });
        }).orElseGet(() -> {
          ActionMessages messages = new ActionMessages();
          messages.add(GLOBAL_MESSAGE, new ActionMessage("form.login.error.unknownuser"));
          saveMessages(request, messages);
          return mapping.getInputForward();
        });
  }
}
