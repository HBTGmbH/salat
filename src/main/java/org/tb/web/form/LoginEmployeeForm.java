package org.tb.web.form;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;

import javax.servlet.http.HttpServletRequest;

/**
 * Form for the employee login
 *
 * @author oda
 */
public class LoginEmployeeForm extends ActionForm {

    /**
     *
     */
    private static final long serialVersionUID = 1L; // 3057468306212305857L;

    private String loginname;

    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getLoginname() {
        return loginname;
    }

    public void setLoginname(String loginname) {
        this.loginname = loginname;
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        loginname = null;
        password = null;
    }

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        if (GenericValidator.isBlankOrNull(loginname)) {
            errors.add("loginname", new ActionMessage("form.login.error.loginname.empty"));
        }
        if (GenericValidator.isBlankOrNull(password)) {
            errors.add("password", new ActionMessage("form.login.error.password.empty"));
        }
        return errors;
    }

}
