package org.tb.jira.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tb.common.domain.AuditedEntity;

@Entity
@Table(name = "jira_ticket")
@Getter
@Setter
@NoArgsConstructor
public class JiraTicket extends AuditedEntity {

  @Column(name = "customerorder_sign", nullable = false)
  private String customerorderSign;

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
}
