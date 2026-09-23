package org.tb.etl.persistence;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tb.etl.domain.ETLRunHistory;

public interface ETLRunHistoryRepository extends JpaRepository<ETLRunHistory, Long> {

  /**
   * @return Anzahl der gelöschten Einträge
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      DELETE FROM ETLRunHistory r
      WHERE r.startedAt < :cutoff
      """)
  int deleteStartedBefore(@Param("cutoff") LocalDateTime cutoff);

}
