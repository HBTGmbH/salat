package org.tb.common.struts;

import static org.apache.struts.action.ActionMessages.GLOBAL_MESSAGE;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts.Globals;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.apache.struts.action.ExceptionHandler;
import org.apache.struts.config.ExceptionConfig;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCodeException;

public class ErrorCodeExceptionHandler extends ExceptionHandler {

  @Override
  public ActionForward execute(Exception ex, ExceptionConfig ae, ActionMapping mapping, ActionForm form,
      HttpServletRequest request, HttpServletResponse response) throws ServletException {
    addToErrors(request, (ErrorCodeException) ex);
    if(ex instanceof AuthorizationException) {
      return mapping.findForward("unauthorized");
    }
    return mapping.getInputForward();
  }

  public void addToErrors(HttpServletRequest request, ErrorCodeException e) {
    ActionMessages messages = convertToActionMessages(e);
    addErrors(request, messages);
  }

  private static ActionMessages convertToActionMessages(ErrorCodeException e) {
    ActionMessages messages = new ActionMessages();
    e.getMessages().forEach(m -> {
      // TR-0015 -> errorcode.tr.0015
      var messageKey = "errorcode." + m.getErrorCode().getCode().replace('-', '.').toLowerCase();
      var arguments = m.getArguments().toArray();
      messages.add(GLOBAL_MESSAGE, new ActionMessage(messageKey, arguments));
    });
    return messages;
  }

  protected void addErrors(HttpServletRequest request, ActionMessages errors) {
    if (errors == null) {
      //  bad programmer! *slap*
      return;
    }

    // get any existing errors from the request, or make a new one
    ActionMessages requestErrors =
        (ActionMessages) request.getAttribute(Globals.ERROR_KEY);

    if (requestErrors == null) {
      requestErrors = new ActionMessages();
    }

    // add incoming errors
    requestErrors.add(errors);

    // if still empty, just wipe it out from the request
    if (requestErrors.isEmpty()) {
      request.removeAttribute(Globals.ERROR_KEY);

      return;
    }

    // Save the errors
    request.setAttribute(Globals.ERROR_KEY, requestErrors);
  }

}
