package org.tb.common.struts;

import static org.apache.struts.action.ActionMessages.GLOBAL_MESSAGE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ServiceFeedbackMessage;

/**
 * Extends struts {@link Action}s with a typed form of execute.
 * @param <F> {@link ActionForm} that binds the form used by this {@link Action}.
 */
public abstract class TypedAction<F extends ActionForm> extends Action {

  public abstract ActionForward executeWithForm(ActionMapping mapping, F form, HttpServletRequest request,
      HttpServletResponse response) throws Exception;

  @Override
  public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
    return executeWithForm(mapping, (F) form, request, response);
  }

  public void addToErrors(HttpServletRequest request, ErrorCode errorCode) {
    ActionMessages messages = new ActionMessages();
    messages.add(
        GLOBAL_MESSAGE,
        // TR-0015 -> errorcode.tr.0015
        new ActionMessage("errorcode." + errorCode.getCode().replace('-', '.').toLowerCase())
    );
    addErrors(request, messages);
  }

  public void addToErrors(HttpServletRequest request, ServiceFeedbackMessage message) {
    ActionMessages messages = new ActionMessages();
    messages.add(
        GLOBAL_MESSAGE,
        // e.g. TR-0015 -> errorcode.tr.0015
        new ActionMessage(
            "errorcode." + message.getErrorCode().getCode().replace('-', '.').toLowerCase(),
            message.getArguments().toArray())
    );
    addErrors(request, messages);
  }

  public void addToMessages(HttpServletRequest request, ErrorCode errorCode) {
    ActionMessages messages = new ActionMessages();
    messages.add(
        GLOBAL_MESSAGE,
        // TR-0015 -> errorcode.tr.0015
        new ActionMessage("errorcode." + errorCode.getCode().replace('-', '.').toLowerCase())
    );
    addMessages(request, messages);
  }

}
