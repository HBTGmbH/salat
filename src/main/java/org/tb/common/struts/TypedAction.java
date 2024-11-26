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
import org.tb.common.exception.ErrorCodeException;

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

}
