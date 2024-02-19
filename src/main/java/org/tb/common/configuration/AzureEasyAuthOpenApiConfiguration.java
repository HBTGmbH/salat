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
@Profile({ "production", "test" })
public class AzureEasyAuthOpenApiConfiguration {

  @Bean
  public OpenAPI customOpenAPI(Optional<BuildProperties> buildProperties, Optional<GitProperties> gitProperties, SalatProperties salatProperties) {
    String formattedBuildTime = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .withLocale(Locale.getDefault())
        .format(buildProperties.isPresent() ? buildProperties.get().getTime() : Instant.now());

    StringBuilder openApiDescription = new StringBuilder();
    openApiDescription.append("Rest API of the SALAT microservice (").append(formattedBuildTime);
    gitProperties.ifPresent(entries -> openApiDescription.append(" / ").append(entries.getCommitId()));
    openApiDescription
        .append(")\n")
        .append("Security has different modes:\n")
        .append("(1) Transparent using Azure EasyAuth when running in SALAT browser session (cookie based)\n")
        .append("(2) oauth2 device code flow\n");

    SecurityScheme easyAuth = new SecurityScheme();
    easyAuth.setType(Type.APIKEY);
    easyAuth.setName("AppServiceAuthSession");
    easyAuth.setIn(In.COOKIE);
    easyAuth.setDescription("Use Cookie from Azure AppService EasyAuth.");

    SecurityScheme oauth2 = new SecurityScheme();
    oauth2.setType(Type.OPENIDCONNECT);
    oauth2.setOpenIdConnectUrl("https://login.microsoftonline.com/0af2c34e-4e40-42d9-89ab-e095b8151308/v2.0/.well-known/openid-configuration");
    oauth2.setDescription("Azure Entra ID.");

    // see https://springdoc.org/faq.html
    return new OpenAPI()
        .info(new Info()
            .title("SALAT Rest API")
            .version(buildProperties.isPresent() ? buildProperties.get().getVersion() : "")
            .description(openApiDescription.toString()))
        .externalDocs(new ExternalDocumentation().description("Confluence").url("https://hbteam.atlassian.net/wiki/spaces/SALAT/pages/1440481391/Rest+API"))
        .schemaRequirement("EasyAuth", easyAuth)
        .schemaRequirement("oauth2", oauth2)
        .security(of(
            new SecurityRequirement().addList("EasyAuth"),
            new SecurityRequirement().addList("oauth2", salatProperties.getAuth().getApiScope())
        ));
  }

}
