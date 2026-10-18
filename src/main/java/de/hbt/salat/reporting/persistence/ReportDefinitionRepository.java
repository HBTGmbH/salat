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
   * The candidates of the command palette (#1232, ADR-0031): every word stands in the name. Not in
   * the SQL — nearly every word stands in some SQL, and the hits would be worthless. No limit: the
   * caller checks who may execute a report first and limits afterwards.
   */
  @Query("""
      select r from ReportDefinition r
      where lower(r.name) like :word1 escape '!'
      and (:word2 is null or lower(r.name) like :word2 escape '!')
      and (:word3 is null or lower(r.name) like :word3 escape '!')
      order by lower(r.name)
      """)
  List<ReportDefinition> findPaletteCandidates(String word1, String word2, String word3);

  /**
   * Seit #1333 ist der Name eindeutig ({@code uk_report_definition_name}). Die Suche liefert
   * trotzdem eine Liste: Der Fall mehrerer Treffer bleibt behandelt ({@code RP-0002}), statt einen
   * Treffer zu raten, falls der Schlüssel je wieder fällt.
   */
  List<ReportDefinition> findAllByName(String name);

}
