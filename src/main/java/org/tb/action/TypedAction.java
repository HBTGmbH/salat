package org.tb.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

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

}
