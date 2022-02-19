package org.tb.configuration;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BuildInformationProvider implements ServletContextListener {

  private final BuildProperties buildProperties;
  private final GitProperties gitProperties;

  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    ServletContext context = servletContextEvent.getServletContext();
    context.setAttribute("buildProperties", buildProperties);
    context.setAttribute("gitProperties", gitProperties);
  }

}
