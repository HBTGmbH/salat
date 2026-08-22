package org.tb.etl.persistence;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tb.etl.domain.ETLExecutionHistory;

public interface ETLExecutionHistoryRepository extends JpaRepository<ETLExecutionHistory, Long> {

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
