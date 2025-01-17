package org.tb.common.struts;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts.action.ActionMapping;

/**
 * Aware of the form population process in Struts, see {@link DelegatingRequestProcessor}.
 * Implementing class should be an {@link org.apache.struts.action.ActionForm}.
 * Use {@link #postPopulate(HttpServletRequest, HttpServletResponse, ActionMapping)} to add
 * handling logic after values have been populated.
 */
public interface PopulateAware {

  void postPopulate(HttpServletRequest request, HttpServletResponse response, ActionMapping mapping);

}
