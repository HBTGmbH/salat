package org.tb.common.configuration;

import java.util.Arrays;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConfigurationLogger  {
  @EventListener
  public void handleContextRefresh(ContextRefreshedEvent event) {
    final Environment env = event.getApplicationContext().getEnvironment();
    StringBuilder sb = new StringBuilder();
    sb.append("====== Environment and configuration ======\n");
    sb.append("Active profiles: " + Arrays.toString(env.getActiveProfiles()) + "\n");
    final MutablePropertySources sources = ((AbstractEnvironment) env).getPropertySources();
    StreamSupport.stream(sources.spliterator(), false)
        .parallel()
        .filter(ps -> ps instanceof EnumerablePropertySource)
        .map(ps -> ((EnumerablePropertySource) ps).getPropertyNames())
        .flatMap(Arrays::stream)
        .distinct()
        .filter(prop -> !(prop.contains("credentials") || prop.contains("password")))
        .sorted()
        .sequential()
        .forEach(prop -> sb.append(prop + " = >" + env.getProperty(prop) + "<\n"));
    sb.append("===========================================\n");
    log.info(sb.toString());
  }

}