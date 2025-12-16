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
@Table(name = "jira_replication_config")
@Getter
@Setter
@NoArgsConstructor
public class JiraReplicationConfig extends AuditedEntity {

  @Column(name = "customerorder_sign", nullable = false)
  private String customerorderSign;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "base_url", nullable = false)
  private String baseUrl;

  @Column(name = "username", nullable = false)
  private String username;

  @Column(name = "password", nullable = false)
  private String password;

  @Column(name = "jql")
  private String jql;

  @Column(name = "parent_field_names")
  private String parentFieldNames; // comma separated field names to look for parent key

  @Column(name = "page_size")
  private Integer pageSize;

  @Column(name = "enabled")
  private Boolean enabled;

  @Column(name = "last_max_updated")
  private LocalDateTime lastMaxUpdated;
}
