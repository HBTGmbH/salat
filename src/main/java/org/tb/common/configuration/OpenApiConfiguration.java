package org.tb.common.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.In;
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
    openApiDescription.append(")\n").append("Use SALAT user access tokens. Header value must be in the form TokenID:TokenSecret");

    SecurityScheme apikey = new SecurityScheme();
    apikey.setType(Type.APIKEY);
    apikey.setName("apikey");
    apikey.setIn(In.HEADER);
    apikey.setDescription("Use SALAT user access tokens. Header value must be in the form TokenID:TokenSecret");

    // see https://springdoc.org/faq.html
    return new OpenAPI()
        .info(new Info()
            .title("SALAT API")
            .version(buildProperties.isPresent() ? buildProperties.get().getVersion() : "")
            .description(openApiDescription.toString()))
            .schemaRequirement("apikey", apikey);
  }

}
