package de.hbt.salat.jira.domain;

import static de.hbt.salat.jira.domain.JiraApiFlavor.SERVER;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

@Entity
@Table(name = "jira_replication_config")
@Getter
@Setter
@NoArgsConstructor
public class JiraReplicationConfig extends AuditedEntity {

  /**
   * The customer order this replication applies to (#1322) — on its own the whole order, or the
   * order of {@link #suborder}. Required: the migration deleted every config whose scope it could
   * not resolve.
   *
   * <p>A reference to master data of the order module (#1368, ADR-0036): read only, no cascade, and
   * nothing here ever calls a setter on it.
   */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customerorder_id", nullable = false,
      foreignKey = @ForeignKey(name = "fk_jira_replication_config_customerorder"))
  private Customerorder customerorder;

  /**
   * The suborder the replication is narrowed to, at any depth; it covers that suborder and the
   * branch below it (#1025). {@code null} means the whole customer order.
   *
   * <p>A rename of the order or the suborder, or moving the suborder to another parent, changes
   * nothing about what is covered (#1322): order and suborder are archived, not deleted (ADR-0012),
   * and the reference stays. #1025 once chose a sign here because the tickets outlive the config
   * (see {@code JiraReplicationConfigService.delete}); that argues against a reference to the
   * <em>config</em>, not against one to order and suborder.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "suborder_id", foreignKey = @ForeignKey(name = "fk_jira_replication_config_suborder"))
  private Suborder suborder;

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

  /**
   * Whether the worklog sync counts only bookings on invoiceable suborders (#1218). Off, every
   * booking of the scope with a ticket reference counts, as it has since #1007.
   *
   * <p>Switching it either way needs nothing of its own: every run sums the whole period again and
   * compares against what was last written, so the next run lowers, removes or restores the
   * worklogs from {@link #worklogSyncFrom} on. Worklogs before that day stay as they are.
   */
  @Column(name = "worklog_sync_invoiceable_only")
  private Boolean worklogSyncInvoiceableOnly;

  @Column(name = "last_max_updated")
  private LocalDateTime lastMaxUpdated;

  /** The id of {@link #customerorder}, read off the reference without loading the order. */
  public Long getCustomerorderId() {
    return customerorder != null ? customerorder.getId() : null;
  }

  /** The id of {@link #suborder}, {@code null} for the whole order; the suborder is not loaded. */
  public Long getSuborderId() {
    return suborder != null ? suborder.getId() : null;
  }

  /**
   * A missing value keeps behaving the way the replication did before Cloud support existed. Since
   * #984 the form always writes a flavor, but the rows that were maintained by hand via SQL until
   * then may still carry none.
   */
  public JiraApiFlavor getApiFlavor() {
    return apiFlavor != null ? apiFlavor : SERVER;
  }
}
