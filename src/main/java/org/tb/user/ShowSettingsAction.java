package org.tb.user;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.common.util.SecureHashUtils;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;

@Component
@RequiredArgsConstructor
public class ShowSettingsAction extends LoginRequiredAction<ShowSettingsForm> {

    private final EmployeeDAO employeeDAO;
    private final UserAccessTokenService userAccessTokenService;
    private final UserService userService;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping,
        ShowSettingsForm settingsForm, HttpServletRequest request,
        HttpServletResponse response) throws Exception {

        request.setAttribute("passwordchanged", false);
        request.setAttribute("userAccessTokens", userAccessTokenService.getTokens(authorizedUser.getEmployeeId()));

        if ("saveSettings".equalsIgnoreCase(request.getParameter("task"))) {
            String landingPage = settingsForm.getLandingPage();

            // get employee
            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
            // set new landingPage and save
            if(userService.setLandingPage(loginEmployee.getId(), landingPage)) {
                loginEmployee.setLandingpage(landingPage);
                request.setAttribute("settingsSaved", true);
                return mapping.findForward("success");
            }else {
              ActionMessages messages = new ActionMessages();
              messages.add("settings", new ActionMessage("main.error.title.text"));
              saveErrors(request, messages);
              return mapping.getInputForward();
            }
        } else if ("changePassword".equalsIgnoreCase(request.getParameter("task"))) {
            ActionMessages errorMessages = validatePassword(request, settingsForm);
            if (!errorMessages.isEmpty()) {
                return mapping.getInputForward();
            }

            // get employee
            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

            // set new password and save
            if(userService.changePassword(loginEmployee.getId(), settingsForm.getNewpassword())) {
                request.setAttribute("passwordchanged", true);
                return mapping.findForward("success");
            } else {
                ActionMessages messages = new ActionMessages();
                messages.add("newpassword", new ActionMessage("form.settings.error.newpassword.violates.rules"));
                saveErrors(request, messages);
                return mapping.getInputForward();
            }
        } else {

            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
            settingsForm.setLandingPage(loginEmployee.getLandingpage());
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
        if (oldPassword == null || !SecureHashUtils.passwordMatches(oldPassword, passwordFromDB)) {
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
