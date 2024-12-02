package org.tb.common.struts;

import static org.springframework.core.NestedExceptionUtils.getRootCause;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.config.ExceptionConfig;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ErrorCodeException;

@Slf4j
public class ServletExceptionHandler extends ErrorCodeExceptionHandler {

  @Override
  public ActionForward execute(Exception ex, ExceptionConfig ae, ActionMapping mapping, ActionForm form,
      HttpServletRequest request, HttpServletResponse response) throws ServletException {
    Throwable cause = getRootCause(ex);
    log.error("ServletException caught. Redirecting to home.", ex);
    var ece = new ErrorCodeException(ErrorCode.XX_UNHANDLED_SERVLET_EXCEPTION, cause.getClass().getName(), cause.getMessage());
    addToErrors(request, ece);
    try {
      response.sendRedirect("/");
      return null;
    } catch (IOException e) {
      throw new ServletException(e);
    }
  }

}
