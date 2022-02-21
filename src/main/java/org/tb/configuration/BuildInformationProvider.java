package org.tb.configuration;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Component;
import org.tb.helper.ServerTimeHelper;

@Component
@RequiredArgsConstructor
public class BuildInformationProvider implements ServletContextListener {

  private final BuildProperties buildProperties;
  private final GitProperties gitProperties;
  private final ServerTimeHelper serverTimeHelper;

  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    ServletContext context = servletContextEvent.getServletContext();
    context.setAttribute("buildProperties", buildProperties);
    context.setAttribute("gitProperties", gitProperties);
    context.setAttribute("serverTimeHelper", serverTimeHelper);
  }

}
