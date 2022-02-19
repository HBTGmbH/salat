package org.tb;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

public class ServletInitializer extends SpringBootServletInitializer {

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    return application.sources(SalatApplication.class);
  }

  @Override
  public void onStartup(ServletContext servletContext) throws ServletException {
    //servletContext.setSessionTimeout(660);
    super.onStartup(servletContext);
  }

}
