package de.hbt.salat.common;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

  /** Parts of a property name that mark its value as confidential. */
  private static final List<String> CONFIDENTIAL =
      List.of("credentials", "password", "secret", "token", "signingkey", "signing-key", "signing_key");

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
        .filter(prop -> !isConfidential(prop))
        .sorted()
        .sequential()
        .forEach(prop -> sb.append(prop + " = >" + env.getProperty(prop) + "<\n"));
    sb.append("===========================================\n");
    log.info(sb.toString());
  }

  /**
   * Whether the value of a property must not reach the log. Case-insensitive: the environment
   * variables arrive with their own names, {@code SPRING_DATASOURCE_PASSWORD} or
   * {@code SALAT_SECRET_KEYS_K1}, and a filter on lower case let them through (#1432).
   */
  static boolean isConfidential(String property) {
    var name = property.toLowerCase(Locale.ROOT);
    return CONFIDENTIAL.stream().anyMatch(name::contains);
  }

}
