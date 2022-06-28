package org.tb.reporting.persistence;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.reporting.domain.ReportDefinition;

@Repository
public interface ReportDefinitionRepository extends PagingAndSortingRepository<ReportDefinition, Long> {

}
