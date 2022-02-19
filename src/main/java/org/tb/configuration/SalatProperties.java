package org.tb.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "salat")
@Data
public class SalatProperties {

  private String url;
  private String mailHost;

}
