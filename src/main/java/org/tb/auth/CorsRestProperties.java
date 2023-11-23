package org.tb.auth;


import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "salat.rest.cors")
@Data
public class CorsRestProperties {

  private List<String> allowedOrigins;

}
