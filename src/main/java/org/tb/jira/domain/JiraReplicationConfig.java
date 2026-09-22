package org.tb.jira.domain;

import static org.tb.jira.domain.JiraApiFlavor.SERVER;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tb.common.domain.AuditedEntity;

@Entity
@Table(name = "jira_replication_config")
@Getter
@Setter
@NoArgsConstructor
public class JiraReplicationConfig extends AuditedEntity {

  /**
   * Where this replication applies (#1025): either a customer order sign for the whole order, or
   * the fully qualified sign of one suborder at any depth, {@code AUFTRAG/01/02}, as
   * {@code Suborder.getCompleteOrderSign()} builds it. A row written before #1025 carries an order
   * sign and therefore means "the whole order" without anything to migrate.
   *
   * <p>Deliberately a sign rather than a foreign key: the tickets outlive the config (see
   * {@code JiraReplicationConfigService.delete}) and the suborder sign alone would not be unique —
   * {@code AUFTRAG/A/01} and {@code AUFTRAG/B/01} may both exist.
   */
  @Column(name = "scope_sign", nullable = false)
  private String scopeSign;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "base_url", nullable = false)
  private String baseUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "api_flavor")
  private JiraApiFlavor apiFlavor;

  /** On JIRA Cloud this is the Atlassian account e-mail — API tokens authenticate as that user. */
  @Column(name = "username", nullable = false)
  private String username;

  /** On JIRA Cloud this is the API token, passed as the HTTP Basic password. */
  @Column(name = "password", nullable = false)
  private String password;

  @Column(name = "jql")
  private String jql;

  @Column(name = "parent_field_names")
  private String parentFieldNames; // comma separated field names to look for parent key

  /**
   * Comma separated JIRA response keys (e.g. {@code customfield_10123}) to read in addition to the
   * fixed field list and to store on the ticket (#881). Standard fields work like custom ones, and
   * an entry may address a part of a field by a dotted path — see {@link JiraFieldConfig}.
   */
  @Column(name = "additional_field_names")
  private String additionalFieldNames;

  /**
   * Comma separated response keys whose value is resolved along the parent chain (#881) — normally a
   * subset of {@link #additionalFieldNames}, but a key named only here is requested as well rather
   * than staying silently empty.
   */
  @Column(name = "inherited_field_names")
  private String inheritedFieldNames;

  @Column(name = "page_size")
  private Integer pageSize;

  @Column(name = "enabled")
  private Boolean enabled;

  /**
   * Whether the run writes the booked hours back to JIRA as worklogs (#1007). Off means the
   * replication stays the pure read it has always been — not a single writing call is made.
   *
   * <p>Switched on, the stored account needs write permission in JIRA, and it is the account the
   * worklogs are authored by: the person who booked never reaches JIRA.
   */
  @Column(name = "worklog_sync_enabled")
  private Boolean worklogSyncEnabled;

  /**
   * The first day the worklog sync covers (#1007). Without it the first run after switching on
   * would write the whole history of the order into JIRA in one go. Bookings before this day are
   * never written, and rows of {@code jira_worklog_sync} before it are never touched — moving the
   * date forward must not read the days it leaves behind as "all bookings are gone".
   */
  @Column(name = "worklog_sync_from")
  private LocalDate worklogSyncFrom;

  @Column(name = "last_max_updated")
  private LocalDateTime lastMaxUpdated;

  /**
   * A missing value keeps behaving the way the replication did before Cloud support existed. Since
   * #984 the form always writes a flavor, but the rows that were maintained by hand via SQL until
   * then may still carry none.
   */
  public JiraApiFlavor getApiFlavor() {
    return apiFlavor != null ? apiFlavor : SERVER;
  }
}
