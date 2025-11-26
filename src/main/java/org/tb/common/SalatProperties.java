package org.tb.common;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "salat")
public class SalatProperties {

  private String url;
  private String mailHost;
  private Auth auth;
  private AuthService authService;

  @Data
  public static class Auth {
    private String apiScope;
    private EasyAuth easyAuth;
    private Logout logout;

    @Data
    public static class EasyAuth {
      private String principalIdHeaderName;
      private OidcIdToken oidcIdToken;
    }

    @Data
    public static class Logout {
      private boolean enabled;
      private String logoutUrl;
    }

    @Data
    public static class OidcIdToken {
      private String principalClaimName;
      private String principalIdClaimName;
      private String headerName;
    }
  }

  @Data
  public static class AuthService {
    private Duration cacheExpiry;
  }

}
