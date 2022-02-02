package org.tb.action;

import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.bdom.Employee;
import org.tb.form.LoginEmployeeForm;
import org.tb.helper.AfterLogin;
import org.tb.persistence.*;
import org.tb.util.SecureHashUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Action class for the login of an employee
 *
 * @author oda, th
 */
@Slf4j
public class LoginEmployeeAction extends TypedAction<LoginEmployeeForm> {


    private EmployeeDAO employeeDAO;
    private AfterLogin afterLogin;

    public void setAfterLogin(AfterLogin afterLogin) {
        this.afterLogin = afterLogin;
    }

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }


    @Override
    public ActionForward executeWithForm(ActionMapping mapping, LoginEmployeeForm loginEmployeeForm, HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.trace("entering {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
        try {
            Employee loginEmployee = employeeDAO.getLoginEmployee(loginEmployeeForm.getLoginname());
            boolean passwordMatches = loginEmployee != null && SecureHashUtils.passwordMatches(
                loginEmployeeForm.getPassword(),
                loginEmployee.getPassword()
            );
            if (!passwordMatches) {
                boolean legacyPasswordMatches = loginEmployee != null && SecureHashUtils.legacyPasswordMatches(
                    loginEmployeeForm.getPassword(), loginEmployee.getPassword()
                );
                if (legacyPasswordMatches) {
                    // employee still has old password form
                    // store password again with new hashing algorithm
                    Employee em = employeeDAO.getEmployeeById(loginEmployee.getId());
                    em.changePassword(loginEmployeeForm.getPassword());
                    loginEmployee.changePassword(loginEmployeeForm.getPassword());
                    employeeDAO.save(em, loginEmployee);
                } else {
                    return loginFailed(request, "form.login.error.unknownuser", mapping);
                }
            }

            String error = afterLogin.handle(request, loginEmployee);
            if (error != null) {
                return loginFailed(request, error, mapping);
            }

            // property passwordchange is set to true if password has been reset (username and password are equal)
            // in this case show the password change site
            if (Boolean.TRUE.equals(loginEmployee.getPasswordchange())) {
                return mapping.findForward("password");
            }

            return mapping.findForward("success");
        } finally {
            log.trace("leaving {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
        }
    }


    private ActionForward loginFailed(HttpServletRequest request, String key, ActionMapping mapping) {
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }
        errors.add(null, new ActionMessage(key));

        saveErrors(request, errors);
        return mapping.getInputForward();
    }
}
