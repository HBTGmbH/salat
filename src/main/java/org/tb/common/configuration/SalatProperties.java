package org.tb.common.configuration;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

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
    private String sessionCookieName;
    private OidcIdToken oidcIdToken;
    private AccessToken accessToken;
    private Refresh refresh;

    @Data
    public static class Refresh {
      private boolean enabled;
      private String refreshUrl;
    }

    @Data
    public static class OidcIdToken {
      private String principalClaimName;
      private String headerName;
    }

    @Data
    public static class AccessToken {
      private String headerName;
      private String expiresOnHeaderName;
    }
  }

  @Data
  public static class AuthService {
    private Duration cacheExpiry;
  }

}
