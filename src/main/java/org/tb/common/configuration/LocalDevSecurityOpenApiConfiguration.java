package org.tb.common.configuration;

import static java.util.List.of;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
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
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalDevSecurityOpenApiConfiguration {

  @Bean
  public OpenAPI customOpenAPI(Optional<BuildProperties> buildProperties, Optional<GitProperties> gitProperties, SalatProperties salatProperties) {
    String formattedBuildTime = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .withLocale(Locale.getDefault())
        .format(buildProperties.isPresent() ? buildProperties.get().getTime() : Instant.now());

    StringBuilder openApiDescription = new StringBuilder();
    openApiDescription.append("DEVELOPMENT Rest API of the SALAT microservice (").append(formattedBuildTime);
    gitProperties.ifPresent(entries -> openApiDescription.append(" / ").append(entries.getCommitId()));
    openApiDescription.append(")\n");

    SecurityScheme employeeSign = new SecurityScheme();
    employeeSign.setType(Type.APIKEY);
    employeeSign.setName("employee-sign");
    employeeSign.setIn(In.QUERY);
    employeeSign.setDescription("Enter sign from employee, e.g. kr");

    // see https://springdoc.org/faq.html
    return new OpenAPI()
        .info(new Info()
            .title("DEVELOPMENT SALAT Rest API")
            .version(buildProperties.isPresent() ? buildProperties.get().getVersion() : "DEVELOPMENT")
            .description(openApiDescription.toString()))
        .externalDocs(new ExternalDocumentation().description("Confluence").url("https://hbteam.atlassian.net/wiki/spaces/SALAT/pages/1440481391/Rest+API"))
        .schemaRequirement("employeeSign", employeeSign)
        .security(of(new SecurityRequirement().addList("employeeSign")));
  }

}
