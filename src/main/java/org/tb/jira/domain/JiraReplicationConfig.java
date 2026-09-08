package org.tb.jira.domain;

import static org.tb.jira.domain.JiraApiFlavor.SERVER;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

  @Column(name = "page_size")
  private Integer pageSize;

  @Column(name = "enabled")
  private Boolean enabled;

  @Column(name = "last_max_updated")
  private LocalDateTime lastMaxUpdated;

  /**
   * These rows are maintained by hand via SQL, so a missing value has to keep behaving the way the
   * replication did before Cloud support existed.
   */
  public JiraApiFlavor getApiFlavor() {
    return apiFlavor != null ? apiFlavor : SERVER;
  }
}
