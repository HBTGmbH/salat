package org.tb.chicoree;

import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import lombok.Data;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;

@Data
public class LoginForm extends ActionForm {

  private String loginname;
  private String password;

  @Override
  public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
    ActionErrors errors = new ActionErrors();
    if(loginname.isBlank()) {
      errors.add("loginname", new ActionMessage("form.login.error.loginname.empty"));
    }
    if(password.isBlank()) {
      errors.add("password", new ActionMessage("form.login.error.password.empty"));
    }
    return errors;
  }

}
