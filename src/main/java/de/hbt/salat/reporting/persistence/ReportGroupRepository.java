package de.hbt.salat.reporting.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.reporting.domain.ReportGroup;

@Repository
public interface ReportGroupRepository extends PagingAndSortingRepository<ReportGroup, Long>,
    CrudRepository<ReportGroup, Long> {

  @Query("select g from ReportGroup g")
  List<ReportGroup> findAllGroups();

  /** The personal group of this login (#1523), of which there is one at most. */
  @Query("select g from ReportGroup g where g.owner.id = :ownerUserId")
  Optional<ReportGroup> findByOwnerUserId(Long ownerUserId);

  /** The groups managers maintain with this name, case aside — the name is to tell them apart. */
  @Query("select g from ReportGroup g where lower(g.name) = lower(:name)")
  List<ReportGroup> findAllByNameIgnoreCase(String name);
}
