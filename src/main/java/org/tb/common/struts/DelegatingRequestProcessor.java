package org.tb.common.struts;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.beanutils.converters.BigDecimalConverter;
import org.apache.commons.beanutils.converters.BigIntegerConverter;
import org.apache.commons.beanutils.converters.BooleanConverter;
import org.apache.commons.beanutils.converters.ByteConverter;
import org.apache.commons.beanutils.converters.CharacterConverter;
import org.apache.commons.beanutils.converters.DoubleConverter;
import org.apache.commons.beanutils.converters.FloatConverter;
import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.beanutils.converters.LongConverter;
import org.apache.commons.beanutils.converters.ShortConverter;
import org.apache.struts.Globals;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionServlet;
import org.apache.struts.action.RequestProcessor;
import org.apache.struts.config.ModuleConfig;
import org.springframework.beans.BeansException;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.tb.common.SalatProperties;

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

    // ensure converters are bound to servlet context to not get garbage collected
    String convertNull = actionServlet.getServletConfig().getInitParameter("convertNull");
    if ("true".equalsIgnoreCase(convertNull) || "yes".equalsIgnoreCase(convertNull)
        || "on".equalsIgnoreCase(convertNull) || "y".equalsIgnoreCase(convertNull)
        || "1".equalsIgnoreCase(convertNull)) {

      Map<Class<?>, Converter> converters = new HashMap<>();
      converters.put(BigDecimal.class, new BigDecimalConverter(null));
      converters.put(BigInteger.class, new BigIntegerConverter(null));
      converters.put(Boolean.class, new BooleanConverter(null));
      converters.put(Byte.class, new ByteConverter(null));
      converters.put(Character.class, new CharacterConverter(null));
      converters.put(Double.class, new DoubleConverter(null));
      converters.put(Float.class, new FloatConverter(null));
      converters.put(Integer.class, new IntegerConverter(null));
      converters.put(Long.class, new LongConverter(null));
      converters.put(Short.class, new ShortConverter(null));

      context.getServletContext().setAttribute("struts.converters", converters);

      ConvertUtils.deregister();
      converters.forEach((clazz, converter) -> ConvertUtils.register(converter, clazz));
    }

    return context;
  }

  protected final WebApplicationContext getWebApplicationContext() {
    return this.webApplicationContext;
  }

  protected Action processActionCreate(HttpServletRequest request, HttpServletResponse response, ActionMapping mapping) {
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

  @Override
  protected void doForward(String uri, HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    try {
      ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
      super.doForward(uri, request, wrapper);
      wrapper.copyBodyToResponse();
    } catch (Exception e) {
      ActionMapping mapping = (ActionMapping) request.getAttribute(Globals.MAPPING_KEY);
      ActionForm form = getActionForm(request, mapping);
      ActionForward forward = processException(request, response, e, form, mapping);
      if(forward != null) {
        processForwardConfig(request, response, forward);
      }
    }
  }

  private static ActionForm getActionForm(HttpServletRequest request, ActionMapping mapping) {
    if ("request".equals(mapping.getScope())) {
      return (ActionForm) request.getAttribute(mapping.getAttribute());
    }
    HttpSession session = request.getSession();
    return (ActionForm) session.getAttribute(mapping.getAttribute());
  }
}
