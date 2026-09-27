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
  private String docsUrl;
  private String apiDocsUrl;
  private Auth auth;
  private AuthService authService;
  private UiState uiState = new UiState();
  private Notifications notifications = new Notifications();
  private Etl etl = new Etl();
  private BookingList bookingList = new BookingList();

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

  @Data
  public static class UiState {
    private String signingKey;
  }

  @Data
  public static class Notifications {
    private int retentionDays = 30;
    private int bellLimit = 10;
  }

  /** The booking list (#1092). */
  @Data
  public static class BookingList {
    /**
     * How many rows the entry "Alle" of the limit select shows at most (#1153). A wide period without any other filter
     * would otherwise render every booking the user may read. Beyond this many hits the list says it is cut; the sums
     * and the spreadsheet still count every hit.
     */
    private int allMaxRows = 10_000;
  }

  @Data
  public static class Etl {
    private History history = new History();

    @Data
    public static class History {
      private int retentionDays = 14;
    }
  }

}
