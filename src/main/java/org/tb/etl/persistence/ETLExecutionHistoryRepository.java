package org.tb.etl.persistence;

import org.tb.etl.domain.ETLExecutionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ETLExecutionHistoryRepository extends JpaRepository<ETLExecutionHistory, Long> {

}
