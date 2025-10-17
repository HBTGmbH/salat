package org.tb.reporting.persistence;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.reporting.domain.ScheduledReportJob;

@Repository
public interface ScheduledReportJobRepository extends CrudRepository<ScheduledReportJob, Long> {

  List<ScheduledReportJob> findByEnabledTrue();

}
