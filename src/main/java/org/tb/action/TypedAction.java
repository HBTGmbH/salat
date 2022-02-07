package org.tb.action;

import static org.apache.struts.action.ActionMessages.GLOBAL_MESSAGE;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.tb.ErrorCode;

/**
 * Extends struts {@link Action}s with a typed form of execute.
 * @param <F> {@link ActionForm} that binds the form used by this {@link Action}.
 */
public abstract class TypedAction<F extends ActionForm> extends Action {

  public abstract ActionForward executeWithForm(ActionMapping mapping, F form, HttpServletRequest request,
      HttpServletResponse response) throws Exception;

  @Override
  public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
      HttpServletResponse response) throws Exception {
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

}
