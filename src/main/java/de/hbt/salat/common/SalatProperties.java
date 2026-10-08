package de.hbt.salat.common;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.ToString;
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
  private Jira jira = new Jira();
  private BookingList bookingList = new BookingList();
  private Runs runs = new Runs();
  private Vacation vacation = new Vacation();
  private Training training = new Training();
  private Secret secret = new Secret();

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

  /** The runs that hold a {@code RUNNING} row as their lock: ETL run and JIRA replication (#1300). */
  @Data
  public static class Runs {
    /**
     * How long a finished run keeps trying to write its outcome while the database is unreachable
     * ({@code RunFinisher}). The value stands in {@code application.yaml}.
     */
    private Duration finishRetryMax;
  }

  /**
   * The vacation order and its suborders without a calculated entitlement (#1341, → {@code SpecialOrders}).
   * Named by sign in {@code application.yaml}; empty means the role is off.
   */
  @Data
  public static class Vacation {
    private String customerorderSign;
    /** Complete order signs ({@code URLAUB/Sonderurlaub}) of suborders of the vacation order. */
    private List<String> doNotCalculateSigns = new ArrayList<>();
  }

  /** The suborders of the regular training (#1341, → {@code SpecialOrders}). */
  @Data
  public static class Training {
    /** Complete order signs ({@code i976/FORTBILDUNG}). */
    private List<String> regularSuborderSigns = new ArrayList<>();
  }

  @Data
  public static class Jira {
    private History history = new History();

    /** The run history of the replications (#1282). */
    @Data
    public static class History {
      private int retentionDays = 14;
    }
  }

  /**
   * The keys of the secret store (#1432, → ADR-0038): {@code SALAT_SECRET_ACTIVEKEYID} and one
   * {@code SALAT_SECRET_KEYS_<ID>} per key, a Base64 encoded 256-bit AES key each. Only ever from the
   * environment, never from an {@code application*.yaml} — {@code SecretKeyConfigurationTest} checks
   * the files. Without an active key the application starts, but stores no secret.
   */
  @Data
  public static class Secret {
    /** The key new values are encrypted with. Lower case letters and digits only. */
    private String activeKeyId;
    /** By key id. A key that is no longer active stays until the start has encrypted its rows anew. */
    @ToString.Exclude
    private Map<String, String> keys = new HashMap<>();
  }

}
