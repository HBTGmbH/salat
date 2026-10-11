package de.hbt.salat.etl.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import de.hbt.salat.etl.domain.ETLExecutionHistory;

public interface ETLExecutionHistoryRepository extends JpaRepository<ETLExecutionHistory, Long> {

  /**
   * Die Ausführungen eines Laufs in der Reihenfolge, in der sie liefen (#1324).
   *
   * <p>Geordnet über die id, nicht über {@code executed_at}: die Spalte hat Sekundengenauigkeit, und
   * kurze Ausführungen teilen sich dieselbe Sekunde. Die id vergibt die Datenbank beim Schreiben, und
   * geschrieben wird jeder Eintrag am Ende seiner Ausführung, einer nach dem anderen.
   */
  @Query("""
      SELECT h FROM ETLExecutionHistory h
      WHERE h.run.id = :runId
      ORDER BY h.id
      """)
  List<ETLExecutionHistory> findByRunInOrderOfExecution(@Param("runId") long runId);

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
