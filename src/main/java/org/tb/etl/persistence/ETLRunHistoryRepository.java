package org.tb.etl.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.domain.ETLRunHistory.Status;

public interface ETLRunHistoryRepository extends JpaRepository<ETLRunHistory, Long> {

  List<ETLRunHistory> findByOrderByStartedAtDesc(Pageable pageable);

  List<ETLRunHistory> findByStatusNotOrderByStartedAtDesc(Status status, Pageable pageable);

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
