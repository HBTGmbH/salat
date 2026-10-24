package de.hbt.salat.reporting.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.domain.ReportGroupCount;

@Repository
public interface ReportDefinitionRepository extends PagingAndSortingRepository<ReportDefinition, Long>,
    CrudRepository<ReportDefinition, Long> {

  /**
   * Every report with its group (#1523), by title. The lists filter in Java: who may run a report is
   * decided per report anyway, and a personal group is searched by the name of its person, which the
   * database of this module does not hold.
   */
  @Query("select r from ReportDefinition r left join fetch r.group order by lower(r.name)")
  List<ReportDefinition> findAllWithGroup();

  /**
   * The reports with this title in this group, case aside (#1523); {@code groupId} {@code null} for the
   * reports without a group, which the unique key of the database cannot keep apart.
   */
  @Query("""
      select r from ReportDefinition r left join r.group g
      where lower(r.name) = lower(:name)
      and (g.id = :groupId or (:groupId is null and g is null))
      """)
  List<ReportDefinition> findAllByGroupAndName(Long groupId, String name);

  /** How many reports each group holds (#1523); a group without any is left out. */
  @Query("""
      select new de.hbt.salat.reporting.domain.ReportGroupCount(g.id, count(r))
      from ReportDefinition r join r.group g
      group by g.id
      """)
  List<ReportGroupCount> countByGroup();

  /** The reports of a deleted group stay, without a group (#1523). */
  @Modifying
  @Query("update ReportDefinition r set r.group = null where r.group.id = :groupId")
  int clearGroup(Long groupId);

}
