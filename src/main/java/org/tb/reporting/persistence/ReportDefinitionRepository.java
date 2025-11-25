package org.tb.reporting.persistence;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.tb.reporting.domain.ReportDefinition;

@Repository
public interface ReportDefinitionRepository extends PagingAndSortingRepository<ReportDefinition, Long>,
    CrudRepository<ReportDefinition, Long> {

  @Query("select r from ReportDefinition r where lower(r.name) like lower(:filter)")
  Iterable<ReportDefinition> findAllByFilter(String filter, Sort by);

}
