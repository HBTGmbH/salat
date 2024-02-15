package org.tb.common.struts;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionServlet;
import org.apache.struts.action.RequestProcessor;
import org.apache.struts.config.ModuleConfig;
import org.springframework.beans.BeansException;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.tb.common.configuration.SalatProperties;

@Slf4j
public class DelegatingRequestProcessor extends RequestProcessor {

  private WebApplicationContext webApplicationContext;

  public void init(ActionServlet actionServlet, ModuleConfig moduleConfig) throws ServletException {
    super.init(actionServlet, moduleConfig);
    if (actionServlet != null) {
      this.webApplicationContext = this.initWebApplicationContext(actionServlet);
    }
  }

  protected WebApplicationContext initWebApplicationContext(ActionServlet actionServlet) throws IllegalStateException {
    WebApplicationContext context = WebApplicationContextUtils.getRequiredWebApplicationContext(actionServlet.getServletContext());
    context.getServletContext().setAttribute("salatProperties", context.getBean(SalatProperties.class));
    return context;
  }

  protected final WebApplicationContext getWebApplicationContext() {
    return this.webApplicationContext;
  }

  protected Action processActionCreate(HttpServletRequest request, HttpServletResponse response, ActionMapping mapping) throws IOException {
    Action action = this.getDelegateAction(mapping);
    if(action == null) {
      throw new RuntimeException("ouch! no bean found for action with id " + mapping.getActionId() + "(Path=" + mapping.getPath() + ")");
    }
    return action;
  }

  protected Action getDelegateAction(ActionMapping mapping) throws BeansException {
    Class<?> actionClassType = this.determineActionClass(mapping);
    return (Action) this.getWebApplicationContext().getBean(actionClassType);
  }

  @SneakyThrows
  public static Class<?> determineActionClass(ActionMapping mapping) {
    if(mapping.getType() == null) {
      throw new RuntimeException("Missing type attribute in struts-config.xml action declaration for path " + mapping.getPath());
    }
    String actionClassName = mapping.getType();
    return ClassUtils.forName(actionClassName, Thread.currentThread().getContextClassLoader());
  }

}
