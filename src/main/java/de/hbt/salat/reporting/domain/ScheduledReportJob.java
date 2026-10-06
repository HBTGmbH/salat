package de.hbt.salat.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.common.domain.AuditedEntity;

@Entity
@Getter
@Setter
@Table(name = "scheduled_report_job")
@NoArgsConstructor
public class ScheduledReportJob extends AuditedEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @ManyToOne
  @JoinColumn(name = "report_definition_id", nullable = false)
  private ReportDefinition reportDefinition;

  @Column(nullable = false)
  private String name;

  @Column(name = "report_parameters", length = 4000)
  private String reportParameters;

  @Column(name = "recipient_emails", nullable = false, length = 1000)
  private String recipientEmails;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "suppress_empty_results", nullable = false)
  private boolean suppressEmptyResults = false;

  @Column(name = "cron_expression")
  private String cronExpression;

  @Column(length = 1000)
  private String description;

  /**
   * The login that owns the scheduled job (#1330), set from the login it was created under. A
   * reference to master data of auth (#1370, ADR-0036): read only, no cascade. Whoever is not a
   * manager sees, changes and deletes only the jobs they own.
   *
   * <p>Not {@code createdby}: that is the login name, and a login name can be changed or
   * anonymized — the owner would lose the record, and whoever got the name next would inherit it
   * (the reason of #1204). {@code createdby} stays as the audit field it is. {@code null} when no
   * login could be assigned; then only a manager may change it.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id")
  private SalatUser owner;

  public ScheduledReportJob(long id) {
    super(id);
  }

  /** The id of {@link #owner}, read off the reference without loading the login. */
  public Long getOwnerUserId() {
    return owner != null ? owner.getId() : null;
  }

}
