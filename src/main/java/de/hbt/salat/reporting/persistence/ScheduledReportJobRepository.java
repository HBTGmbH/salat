package de.hbt.salat.reporting.persistence;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.reporting.domain.ScheduledReportJob;

@Repository
public interface ScheduledReportJobRepository extends CrudRepository<ScheduledReportJob, Long> {

  List<ScheduledReportJob> findByEnabledTrue();

  List<ScheduledReportJob> findByCreatedby(String createdby);

}
