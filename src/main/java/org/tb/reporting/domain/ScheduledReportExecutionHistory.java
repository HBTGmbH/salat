package org.tb.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "scheduled_report_execution_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ScheduledReportExecutionHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "job_id")
  private long jobId;

  @Column(name = "job_name")
  private String jobName;

  @Column(name = "report_definition_id")
  private Long reportDefinitionId;

  @Column(name = "report_definition_name")
  private String reportDefinitionName;

  @Column(name = "executed_at")
  private LocalDateTime executedAt;

  private boolean success;

  @Column(name = "message", length = 4000)
  private String message;
}
