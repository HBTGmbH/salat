package org.tb.jira.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.tb.common.domain.AuditedEntity;

@Entity
@Table(name = "jira_ticket")
@Getter
@Setter
@NoArgsConstructor
public class JiraTicket extends AuditedEntity {

  /** The scope of the replication that fetched this ticket — see {@code JiraReplicationConfig}. */
  @Column(name = "scope_sign", nullable = false)
  private String scopeSign;

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
}
