package de.hbt.salat.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * One execution of a scheduled report job. The entry outlives the job and the report it names: deleting
 * either leaves the reference empty ({@code ON DELETE SET NULL}), and the names stored with the entry
 * keep it readable (#1366).
 */
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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_id", foreignKey = @ForeignKey(name = "fk_scheduled_report_execution_history_job"))
  @OnDelete(action = OnDeleteAction.SET_NULL)
  private ScheduledReportJob job;

  @Column(name = "job_name")
  private String jobName;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "report_definition_id",
      foreignKey = @ForeignKey(name = "fk_scheduled_report_execution_history_report"))
  @OnDelete(action = OnDeleteAction.SET_NULL)
  private ReportDefinition reportDefinition;

  @Column(name = "report_definition_name")
  private String reportDefinitionName;

  @Column(name = "executed_at")
  private LocalDateTime executedAt;

  private boolean success;

  @Column(name = "message", length = 4000)
  private String message;
}
