package org.tb.web.action;

import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.util.MessageResources;
import org.apache.struts.util.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tb.bdom.Employee;
import org.tb.bdom.Warning;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.MessageFormat;
import java.util.Locale;

import static java.util.Collections.singletonList;

/**
 * Parent action class for the actions of an employee who is correctly logged in.
 * Child action classes will implement method 'executeAuthenticated'.
 *
 * @author oda
 */
public abstract class LoginRequiredAction extends Action {
    private static final Logger LOG = LoggerFactory.getLogger(LoginRequiredAction.class);

    @Override
    public final ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (request.getSession().getAttribute("errors") != null) {
            request.getSession().removeAttribute("errors");
        }
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        if (isAllowedForRestrictedUsers() || (loginEmployee != null && !loginEmployee.isRestricted())) {
            LOG.trace("entering {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
            try {
                return executeAuthenticated(mapping, form, request, response);
            } finally {
                LOG.trace("leaving {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
            }
        } else if (loginEmployee != null) {
            LOG.warn("The user ('{}',{}) tried to access the Action {}!", new Object[]{loginEmployee.getSign(), loginEmployee.getStatus(), getClass().getSimpleName()});

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

    /**
     * To be implemented by child classes.
     */
    protected abstract ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception;

    /**
     * This action may be allowed for restricted users
     *
     * @return whether this action may be performed by restricted users
     */
    protected boolean isAllowedForRestrictedUsers() {
        return false;
    }
}
