package org.tb.common;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BuildInformationProvider implements ServletContextListener, InitializingBean {

  private final ServerTimeHelper serverTimeHelper;
  private final ConfigurableEnvironment environment;
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

    // add to environment
    var gitProps = StreamSupport
        .stream(gitProperties.spliterator(), false)
        .collect(Collectors.toMap(entry -> "git." + entry.getKey(), entry -> (Object) entry.getValue()));
    environment.getPropertySources().addLast(new MapPropertySource("git", gitProps));
    environment.getPropertySources().addLast(buildProperties.toPropertySource());
  }
}
