package org.tb.etl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ein ETL-Lauf als Ganzes (#573) — was {@link ETLExecutionHistory} pro Definition und
 * Referenzperiode festhält, hält diese Tabelle einmal für den ganzen Lauf fest.
 *
 * <p>Die Zeile wird beim Start geschrieben, nicht am Ende. Nur so ist ein Lauf, der nie zu Ende
 * kommt, von einem zu unterscheiden, der nie begann: er bleibt als {@link Status#RUNNING} ohne
 * {@code finishedAt} stehen. Ein Lauf, der gar nicht erst startete — abgeschaltete Anwendung zur
 * Startzeit —, bleibt eine Lücke zwischen zwei Zeilen; mehr kann die Datenbank darüber nicht sagen.
 *
 * <p>Bewusst eine eigene Tabelle statt einer Zeile ohne Definition in {@code etl_execution_history}:
 * dort steht in jeder Zeile eine ETL-Definition, und die ETL- und Report-SQLs in der Datenbank
 * gruppieren über {@code etl_name}.
 */
@Entity
@Table(name = "etl_run_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ETLRunHistory {

  public enum Status {
    RUNNING,
    SUCCEEDED,
    FAILED
  }

  public enum Trigger {
    /** Der nächtliche Lauf. */
    SCHEDULED,
    /** Über die REST-Schnittstelle angestoßen. */
    MANUAL
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  /** {@code null}, solange der Lauf läuft — und dauerhaft, wenn er nie zu Ende kam. */
  @Column(name = "finished_at")
  private LocalDateTime finishedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 20)
  private Status status;

  @Enumerated(EnumType.STRING)
  @Column(name = "triggered_by", length = 20)
  private Trigger triggeredBy;

  @Column(name = "date_from")
  private LocalDate dateFrom;

  @Column(name = "date_until")
  private LocalDate dateUntil;

  @Column(name = "message", length = 4000)
  private String message;

}
