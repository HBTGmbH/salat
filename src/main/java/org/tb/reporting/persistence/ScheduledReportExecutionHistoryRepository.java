package org.tb.reporting.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tb.reporting.domain.ScheduledReportExecutionHistory;

@Repository
public interface ScheduledReportExecutionHistoryRepository extends JpaRepository<ScheduledReportExecutionHistory, Long> {
}
