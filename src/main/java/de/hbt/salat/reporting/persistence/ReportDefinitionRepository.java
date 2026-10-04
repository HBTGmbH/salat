package de.hbt.salat.reporting.persistence;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.reporting.domain.ReportDefinition;

@Repository
public interface ReportDefinitionRepository extends PagingAndSortingRepository<ReportDefinition, Long>,
    CrudRepository<ReportDefinition, Long> {

  @Query("select r from ReportDefinition r where lower(r.name) like lower(:filter)")
  Iterable<ReportDefinition> findAllByFilter(String filter, Sort by);

  /**
   * Seit #1333 ist der Name eindeutig ({@code uk_report_definition_name}). Die Suche liefert
   * trotzdem eine Liste: Der Fall mehrerer Treffer bleibt behandelt ({@code RP-0002}), statt einen
   * Treffer zu raten, falls der Schlüssel je wieder fällt.
   */
  List<ReportDefinition> findAllByName(String name);

}
