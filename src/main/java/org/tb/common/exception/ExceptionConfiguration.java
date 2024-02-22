package org.tb.common.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

@Configuration
public class ExceptionConfiguration {

  @Bean
  public ErrorAttributes errorAttributes() {
    return new SalatErrorAttributes();
  }

  public class SalatErrorAttributes implements ErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
      Map<String, Object> errorAttributes = new HashMap<>();
      errorAttributes.put("timestamp", new Date());

      Integer status = getAttribute(webRequest, RequestDispatcher.ERROR_STATUS_CODE);
      Throwable error = getError(webRequest);

      if (error != null) {
        while (error instanceof ServletException && error.getCause() != null) {
          error = error.getCause();
        }
        errorAttributes.put("exception", error.getClass().getName());
        addStackTrace(errorAttributes, error);

        if(error instanceof ResponseStatusException e) {
          status = e.getStatusCode().value();
          errorAttributes.put("message", e.getReason());
        } else {
          errorAttributes.put("message", error.getMessage());
        }
      }

      if (status == null) {
        errorAttributes.put("status", 999);
        errorAttributes.put("error", "None");
      } else {
        errorAttributes.put("status", status);
        try {
          errorAttributes.put("error", HttpStatus.valueOf(status).getReasonPhrase());
        }
        catch (Exception ex) {
          // Unable to obtain a reason
          errorAttributes.put("error", "Http Status " + status);
        }
      }

      String path = getAttribute(webRequest, RequestDispatcher.ERROR_REQUEST_URI);
      if (path != null) {
        errorAttributes.put("path", path);
      }

      return errorAttributes;
    }

    @Override
    public Throwable getError(WebRequest webRequest) {
      return getAttribute(webRequest, RequestDispatcher.ERROR_EXCEPTION);
    }

    private void addStackTrace(Map<String, Object> errorAttributes, Throwable error) {
      StringWriter stackTrace = new StringWriter();
      error.printStackTrace(new PrintWriter(stackTrace));
      stackTrace.flush();
      errorAttributes.put("trace", stackTrace.toString());
    }

    private <T> T getAttribute(RequestAttributes requestAttributes, String name) {
      return (T) requestAttributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
    }

  }

}
