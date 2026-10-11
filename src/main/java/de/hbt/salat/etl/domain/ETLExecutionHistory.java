package de.hbt.salat.etl.domain;

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
 * Eine Definition in einer Referenzperiode, wie sie in einem ETL-Lauf ausgeführt wurde.
 *
 * <p>Seit #1324 gehört jeder Eintrag über {@code run_id} zu seinem {@link ETLRunHistory Lauf}.
 * Einträge aus der Zeit vor der Laufhistorie (#573), die die Migration keinem Lauf eindeutig
 * zuordnen konnte, bleiben ohne Lauf und fallen mit der Aufbewahrungsfrist weg.
 *
 * <p><b>{@code ON DELETE CASCADE}</b>: ohne seinen Lauf bedeutet ein Eintrag nichts mehr, und das
 * Aufräumen ({@code ETLExecutionHistoryCleanupService}) misst die Frist an zwei verschiedenen
 * Zeitpunkten — am Start des Laufs und am Ende der einzelnen Ausführung. Ein Lauf, der kurz vor der
 * Grenze begann und danach noch Definitionen ausführte, hinterließe sonst Einträge, an deren
 * Fremdschlüssel das Löschen des Laufs scheiterte, und mit ihm das ganze nächtliche Aufräumen.
 */
@Entity
@Table(name = "etl_execution_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ETLExecutionHistory {

  /**
   * Die erste Zeile der Meldung beginnt hiermit, gefolgt von der Referenzperiode — so schreibt sie
   * {@code ETLService}, so liest sie die Anzeige der Ausführungen eines Laufs.
   */
  public static final String DATE_RANGE_PREFIX = "Date Range: ";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Der Lauf, in dem die Ausführung stattfand; {@code null} nur bei Einträgen von vor #1324. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "run_id", foreignKey = @ForeignKey(name = "fk_etl_execution_history_run"))
  @OnDelete(action = OnDeleteAction.CASCADE)
  private ETLRunHistory run;

  @Column(name = "etl_id")
  private long etlId;

  @Column(name = "etl_name")
  private String etlName;

  @Column(name = "executed_at")
  private LocalDateTime executedAt;

  private boolean success;

  @Column(name = "message", length = 4000)
  private String message;

}
