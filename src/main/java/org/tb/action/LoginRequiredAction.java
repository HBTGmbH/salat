package org.tb.action;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.singletonList;
import static org.tb.GlobalConstants.EMPLOYEE_STATUS_ADM;
import static org.tb.GlobalConstants.EMPLOYEE_STATUS_BL;
import static org.tb.GlobalConstants.EMPLOYEE_STATUS_PV;

import java.text.MessageFormat;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.util.MessageResources;
import org.apache.struts.util.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.tb.bdom.AuthorizedUser;
import org.tb.bdom.Employee;
import org.tb.bdom.Warning;

/**
 * Parent action class for the actions of an employee who is correctly logged in.
 * Child action classes will implement method 'executeAuthenticated'.
 *
 * @author oda
 */
@Slf4j
public abstract class LoginRequiredAction<F extends ActionForm> extends TypedAction<F> {

    private AuthorizedUser authorizedUser;

    @Override
    public final ActionForward executeWithForm(ActionMapping mapping, F form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (request.getSession().getAttribute("errors") != null) {
            request.getSession().removeAttribute("errors");
        }
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        initAuthorizedUser(loginEmployee);

        if (loginEmployee != null && (isAllowedForRestrictedUsers() || !loginEmployee.isRestricted())) {
            log.trace("entering {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
            try {
                return executeAuthenticated(mapping, form, request, response);
            } finally {
                log.trace("leaving {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
            }
        } else if (loginEmployee != null) {
            log.warn("The user ('{}',{}) tried to access the Action {}!", new Object[]{loginEmployee.getSign(), loginEmployee.getStatus(), getClass().getSimpleName()});

            MessageResources resources = getResources(request);
            Locale locale = RequestUtils.getUserLocale(request, null);

            Warning warning = new Warning();
            warning.setSort(resources.getMessage(locale, "main.authorization.access.restricted.sort"));
            String text = MessageFormat.format(resources.getMessage(locale, "main.authorization.access.restricted.text"), loginEmployee.getSign(), loginEmployee.getStatus(), mapping.getPath());
            warning.setText(text);
            request.getSession().setAttribute("warnings", singletonList(warning));
            request.getSession().setAttribute("warningsPresent", true);
            return mapping.findForward("showWelcome");
        } else {
            return mapping.findForward("login");
        }
    }

    private void initAuthorizedUser(Employee loginEmployee) {
        if(loginEmployee != null) {
            authorizedUser.setAuthenticated(true);
            authorizedUser.setEmployeeId(loginEmployee.getId());
            authorizedUser.setSign(loginEmployee.getSign());
            authorizedUser.setRestricted(TRUE.equals(loginEmployee.getRestricted()));
            boolean isManager = loginEmployee.getStatus().equals(EMPLOYEE_STATUS_BL) || loginEmployee.getStatus().equals(EMPLOYEE_STATUS_PV);
            authorizedUser.setManager(isManager);
            boolean isAdmin = loginEmployee.getStatus().equals(EMPLOYEE_STATUS_ADM);
            authorizedUser.setAdmin(isAdmin);
        } else {
            authorizedUser.setAuthenticated(false);
        }
    }

    /**
     * To be implemented by child classes.
     */
    protected abstract ActionForward executeAuthenticated(ActionMapping mapping, F form, HttpServletRequest request, HttpServletResponse response) throws Exception;

    /**
     * This action may be allowed for restricted users
     *
     * @return whether this action may be performed by restricted users
     */
    protected boolean isAllowedForRestrictedUsers() {
        return false;
    }

    @Autowired
    public void setAuthorizedUser(AuthorizedUser authorizedUser) {
        this.authorizedUser = authorizedUser;
    }

}
