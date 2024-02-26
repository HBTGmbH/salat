package org.tb.common;

import java.time.Duration;
import java.util.List;
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
    private String sessionCookieName;
    private String apiScope;
    private OidcIdToken oidcIdToken;
    private AccessToken accessToken;
    private Logout logout;
    private Refresh refresh;

    @Data
    public static class Logout {
      private boolean enabled;
      private String logoutUrl;
    }

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
