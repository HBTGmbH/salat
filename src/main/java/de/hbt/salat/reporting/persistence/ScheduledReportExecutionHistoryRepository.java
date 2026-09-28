package de.hbt.salat.reporting.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.reporting.domain.ScheduledReportExecutionHistory;

@Repository
public interface ScheduledReportExecutionHistoryRepository extends JpaRepository<ScheduledReportExecutionHistory, Long> {
}
