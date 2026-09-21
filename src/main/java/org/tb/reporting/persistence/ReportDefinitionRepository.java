package org.tb.reporting.persistence;

import java.util.List;
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

  /**
   * Der Name eines Reports ist nicht eindeutig (report_definition hat keine Unique-Constraint),
   * deshalb liefert die Suche eine Liste — wer über den Namen adressiert, muss den mehrdeutigen
   * Fall behandeln statt einen Treffer zu raten.
   */
  List<ReportDefinition> findAllByName(String name);

}
