package org.tb.configuration;

import java.util.Properties;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Component;
import org.tb.common.ServerTimeHelper;

@Component
@RequiredArgsConstructor
public class BuildInformationProvider implements ServletContextListener, InitializingBean {

  private final ServerTimeHelper serverTimeHelper;
  private BuildProperties buildProperties;
  private GitProperties gitProperties;

  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    ServletContext context = servletContextEvent.getServletContext();
    context.setAttribute("buildProperties", buildProperties);
    context.setAttribute("gitProperties", gitProperties);
    context.setAttribute("serverTimeHelper", serverTimeHelper);
  }

  @Autowired(required = false)
  public void setBuildProperties(BuildProperties buildProperties) {
    this.buildProperties = buildProperties;
  }

  @Autowired(required = false)
  public void setGitProperties(GitProperties gitProperties) {
    this.gitProperties = gitProperties;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    if(buildProperties == null) {
      Properties entries = new Properties();
      entries.setProperty("time", "now");
      entries.setProperty("version", "local_dev_build");
      buildProperties = new BuildProperties(entries);
    }
    if(gitProperties == null) {
      Properties entries = new Properties();
      entries.setProperty("commit.time", "now");
      entries.setProperty("build.time", "now");
      gitProperties = new GitProperties(entries);
    }
  }

}
