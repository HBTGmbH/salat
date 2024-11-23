package org.tb.auth;

import static java.util.Collections.singletonList;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.text.MessageFormat;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.util.MessageResources;
import org.apache.struts.util.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.tb.common.Warning;
import org.tb.common.struts.TypedAction;

/**
 * Parent action class for the actions of an employee who is correctly logged in.
 * Child action classes will implement method 'executeAuthenticated'.
 *
 * @author oda
 */
@Slf4j
public abstract class LoginRequiredAction<F extends ActionForm> extends TypedAction<F> {

    @Autowired
    protected AuthorizedUser authorizedUser;

    @Override
    public final ActionForward executeWithForm(ActionMapping mapping, F form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (request.getSession().getAttribute("errors") != null) {
            request.getSession().removeAttribute("errors");
        }

        if (authorizedUser.isAuthenticated() && (isAllowedForRestrictedUsers() || !authorizedUser.isRestricted())) {
            log.trace("entering {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
            try {
                return executeAuthenticated(mapping, form, request, response);
            } finally {
                log.trace("leaving {}.{}() ...", getClass().getSimpleName(), Thread.currentThread().getStackTrace()[1].getMethodName());
            }
        } else if (authorizedUser.isAuthenticated()) {
            log.warn("The user ('{}') tried to access the Action {}!", new Object[]{ authorizedUser.getSign(), getClass().getSimpleName()});

            MessageResources resources = getResources(request);
            Locale locale = RequestUtils.getUserLocale(request, null);

            Warning warning = new Warning();
            warning.setSort(resources.getMessage(locale, "main.authorization.access.restricted.sort"));
            String text = MessageFormat.format(resources.getMessage(locale, "main.authorization.access.restricted.text"), authorizedUser.getSign(), mapping.getPath());
            warning.setText(text);
            request.getSession().setAttribute("warnings", singletonList(warning));
            request.getSession().setAttribute("warningsPresent", true);
            return mapping.findForward("showWelcome");
        } else {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Not logged in!");
            return null;
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

}
