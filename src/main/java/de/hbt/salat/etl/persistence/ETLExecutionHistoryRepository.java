package de.hbt.salat.etl.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import de.hbt.salat.etl.domain.ETLDefinitionInRun;
import de.hbt.salat.etl.domain.ETLExecutionHistory;

public interface ETLExecutionHistoryRepository extends JpaRepository<ETLExecutionHistory, Long> {

  /**
   * Eine Seite der Ausführungen eines Laufs in der Reihenfolge, in der sie liefen (#1324).
   *
   * <p>Geordnet über die id, nicht über {@code executed_at}: die Spalte hat Sekundengenauigkeit, und
   * kurze Ausführungen teilen sich dieselbe Sekunde. Die id vergibt die Datenbank beim Schreiben, und
   * geschrieben wird jeder Eintrag am Ende seiner Ausführung, einer nach dem anderen.
   *
   * @param etlIds nur die Ausführungen dieser Definitionen — die lesbaren, gegebenenfalls auf eine
   *     gefiltert; nie leer, {@code IN ()} ist kein gültiges SQL
   * @param failedOnly nur die fehlgeschlagenen
   */
  @Query("""
      SELECT h FROM ETLExecutionHistory h
      WHERE h.run.id = :runId
        AND h.etlId IN :etlIds
        AND (:failedOnly = false OR h.success = false)
      ORDER BY h.id
      """)
  Page<ETLExecutionHistory> findByRunInOrderOfExecution(@Param("runId") long runId,
                                                        @Param("etlIds") Collection<Long> etlIds,
                                                        @Param("failedOnly") boolean failedOnly,
                                                        Pageable pageable);

  /**
   * Die ids aller Ausführungen eines Laufs in der Reihenfolge, in der sie liefen — die Nummer einer
   * Ausführung im Lauf ist ihr Platz in dieser Liste (#1324). Auch für einen Lauf über die ganze
   * Historie sind das nur einige zehntausend Zahlen.
   */
  @Query("""
      SELECT h.id FROM ETLExecutionHistory h
      WHERE h.run.id = :runId
      ORDER BY h.id
      """)
  List<Long> findIdsByRunInOrderOfExecution(@Param("runId") long runId);

  /** Je Definition, wie oft sie im Lauf ausgeführt wurde und wie oft davon ohne Erfolg (#1324). */
  @Query("""
      SELECT new de.hbt.salat.etl.domain.ETLDefinitionInRun(
               h.etlId, MAX(h.etlName), COUNT(h), SUM(CASE WHEN h.success = false THEN 1 ELSE 0 END))
      FROM ETLExecutionHistory h
      WHERE h.run.id = :runId
      GROUP BY h.etlId
      """)
  List<ETLDefinitionInRun> countByDefinitionInRun(@Param("runId") long runId);

  /**
   * @return Anzahl der gelöschten Einträge
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      DELETE FROM ETLExecutionHistory h
      WHERE h.executedAt < :cutoff
      """)
  int deleteExecutedBefore(@Param("cutoff") LocalDateTime cutoff);

}
