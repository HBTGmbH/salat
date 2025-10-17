package org.tb.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import org.tb.common.domain.AuditedEntity;

@Entity
@Getter
@Setter
@Table(name = "scheduled_report_job")
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

  @Column(name = "cron_expression")
  private String cronExpression;

  @Column(length = 1000)
  private String description;

}
