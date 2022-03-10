package org.tb.common.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.Type;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Optional;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

  @Bean
  public OpenAPI customOpenAPI(Optional<BuildProperties> buildProperties, Optional<GitProperties> gitProperties) {
    String formattedBuildTime = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .withLocale(Locale.getDefault())
        .format(buildProperties.isPresent() ? buildProperties.get().getTime() : Instant.now());

    StringBuilder openApiDescription = new StringBuilder();
    openApiDescription.append("This is the API of the SALAT microservice (").append(formattedBuildTime);
    gitProperties.ifPresent(entries -> openApiDescription.append(" / ").append(entries.getCommitId()));
    openApiDescription.append(")\n").append("Use SALAT user access tokens. Token ID = username, Token secret = password.");

    SecurityScheme basicAuth = new SecurityScheme();
    basicAuth.setType(Type.HTTP);
    basicAuth.setName("basicAuth");
    basicAuth.setScheme("basic");
    basicAuth.setDescription("Use SALAT user access tokens. Token ID = username, Token secret = password.");

    // see https://springdoc.org/faq.html
    return new OpenAPI()
        .info(new Info()
            .title("SALAT API")
            .version(buildProperties.isPresent() ? buildProperties.get().getVersion() : "")
            .description(openApiDescription.toString()))
            .schemaRequirement("basicAuth", basicAuth);
  }

}
