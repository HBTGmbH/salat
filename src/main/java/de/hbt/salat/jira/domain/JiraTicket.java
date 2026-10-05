package de.hbt.salat.jira.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * A ticket replicated from JIRA, kept per scope — the pair of customer order and suborder.
 *
 * <p>Issue key and JIRA id are unique within a scope, and the run looks tickets up by scope and
 * {@code updated_ts}. Those keys live in the changelog only (changeset 150, #1372): a {@code null}
 * suborder means the whole order, and {@code NULL} is never equal to {@code NULL} in a unique key,
 * so the suborder enters them as {@code COALESCE(suborder_id, 0)} — a functional key part the
 * schema generator of the tests cannot express.
 */
@Entity
@Table(name = "jira_ticket")
@Getter
@Setter
@NoArgsConstructor
public class JiraTicket extends AuditedEntity {

  /**
   * The customer order of the scope this ticket was fetched under (#1323) — taken from the
   * replication, see {@code JiraReplicationConfig}. Required: the migration deleted every ticket
   * whose scope it could not resolve.
   *
   * <p>The ticket outlives the replication that fetched it (#1025): deleting the config leaves the
   * ticket here, and a new replication on the same order and suborder finds it again. That is why it
   * refers to order and suborder rather than to the config — and why as a reference, not by sign: a
   * rename or a move in the order tree changes nothing about which scope it belongs to. Master data
   * of the order module (#1368, ADR-0036): read only, no cascade.
   */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customerorder_id", nullable = false,
      foreignKey = @ForeignKey(name = "fk_jira_ticket_customerorder"))
  private Customerorder customerorder;

  /** The suborder of the scope, {@code null} when the replication covers the whole order (#1323). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "suborder_id", foreignKey = @ForeignKey(name = "fk_jira_ticket_suborder"))
  private Suborder suborder;

  @Column(name = "jira_id", nullable = false)
  private Long jiraId;

  @Column(name = "issue_key", nullable = false)
  private String key;

  @Column(name = "summary")
  private String summary;

  @Column(name = "issue_type")
  private String issueType;

  @Column(name = "labels")
  private String labels;

  @Column(name = "parent_key")
  private String parentKey;

  @Column(name = "top_level_key")
  private String topLevelKey;

  @Column(name = "created_ts")
  private LocalDateTime createdTs;

  @Column(name = "updated_ts")
  private LocalDateTime updatedTs;

  /**
   * The additional fields of the replication config, as they are set on this ticket itself (#881).
   * Keys are JIRA response keys, so the module carries no customer meaning.
   *
   * <p>{@code null} rather than an empty map when nothing is configured or nothing is set: a native
   * {@code json} column cannot hold an empty string, and {@code JSON_EXTRACT} on {@code NULL}
   * answers {@code NULL} instead of aborting the whole statement.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "custom_fields")
  private Map<String, String> customFields;

  /**
   * The resolved value of every inherited field, together with where it came from (#881) — an own
   * value with {@code from = null}, otherwise the value of the nearest ancestor that has one. Fields
   * without a value anywhere in the chain are absent rather than present and empty.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "custom_fields_effective")
  private Map<String, ResolvedFieldValue> customFieldsEffective;

  /**
   * Which field configuration the two columns above were written with. A ticket whose hash differs
   * from the config's current one is rewritten on the next run even though JIRA reports it as
   * unchanged — see {@link JiraFieldConfig#hash()}.
   */
  @Column(name = "field_config_hash", length = 64)
  private String fieldConfigHash;

  /** The id of {@link #customerorder}, read off the reference without loading the order. */
  public Long getCustomerorderId() {
    return customerorder != null ? customerorder.getId() : null;
  }

  /** The id of {@link #suborder}, {@code null} for the whole order; the suborder is not loaded. */
  public Long getSuborderId() {
    return suborder != null ? suborder.getId() : null;
  }
}
