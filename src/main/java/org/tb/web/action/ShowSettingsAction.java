package org.tb.web.action;

import org.apache.struts.action.*;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;
import org.tb.util.SecureHashUtils;
import org.tb.web.form.ShowSettingsForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ShowSettingsAction extends LoginRequiredAction {

    private EmployeeDAO employeeDAO;

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping,
                                                 ActionForm form, HttpServletRequest request,
                                                 HttpServletResponse response) throws Exception {

        ShowSettingsForm settingsForm = (ShowSettingsForm) form;
        request.getSession().setAttribute("passwordchanged", false);

        if (request.getParameter("task") != null) {
            if (request.getParameter("task").equalsIgnoreCase("changePassword")) {

                ActionMessages errorMessages = validatePassword(request, settingsForm);
                if (errorMessages.size() > 0) {
                    return mapping.getInputForward();
                }

                // get employee
                Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

                // set new password and save
                Employee em = employeeDAO.getEmployeeById(loginEmployee.getId());
                em.changePassword(settingsForm.getNewpassword());
                loginEmployee.changePassword(settingsForm.getNewpassword());
                employeeDAO.save(em, loginEmployee);


                request.getSession().setAttribute("passwordchanged", true);
                return mapping.findForward("success");

            } else {
                // unknown task -> standard procedure
                return mapping.findForward("success");
            }

        } else {

            // task == null -> standard procedure
            return mapping.findForward("success");
        }


    }

    private ActionMessages validatePassword(HttpServletRequest request,
                                            ShowSettingsForm settingsForm) {

        ActionMessages errors = getErrors(request);
        if (errors == null) errors = new ActionMessages();

        String oldPassword = settingsForm.getOldpassword();
        String newPassword = settingsForm.getNewpassword();
        String confirmPassword = settingsForm.getConfirmpassword();

        //old password
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        String passwordFromDB = employeeDAO.getEmployeeById(loginEmployee.getId()).getPassword();
        if (oldPassword == null || !SecureHashUtils.makeMD5(oldPassword).equals(passwordFromDB)) {
            errors.add("oldpassword", new ActionMessage("form.settings.error.oldpassword.false"));
        }

        // new password
        if (newPassword == null || newPassword.length() < GlobalConstants.EMPLOYEE_PASSWORD_MIN_LENGTH) {
            // new password is missing or too short
            errors.add("newpassword", new ActionMessage("form.settings.error.newpassword.tooshort"));
        } else if (newPassword.length() > GlobalConstants.EMPLOYEE_PASSWORD_MAX_LENGTH) {
            // new password is too long
            errors.add("newpassword", new ActionMessage("form.settings.error.newpassword.toolong"));
        }

        // confirm new password
        if (confirmPassword == null) {
            // confirm password is missing
            errors.add("confirmpassword", new ActionMessage("form.settings.error.confirmpassword.missing"));
        } else if (!confirmPassword.equals(newPassword)) {
            // confirm password does not match with new password
            errors.add("confirmpassword", new ActionMessage("form.settings.error.confirmpassword.false"));
        }


        saveErrors(request, errors);
        return errors;
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
