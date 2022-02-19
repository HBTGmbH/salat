package org.tb.configuration;

import org.apache.struts.Globals;
import org.apache.struts.action.ActionServlet;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StrutsConfiguration {

  @Bean
  public ServletRegistrationBean<ActionServlet> actionServlet() {
    var bean = new ServletRegistrationBean<>(new ActionServlet());
    bean.addInitParameter("config", "/WEB-INF/struts-config.xml");
    bean.setLoadOnStartup(1);
    bean.addUrlMappings("/do/*");
    return bean;
  }

  @Bean
  public ServletContextInitializer addStrutsUrlMappingInfo() {
    return servletContext -> servletContext.setAttribute(Globals.SERVLET_KEY, "/do/*");
  }

}
