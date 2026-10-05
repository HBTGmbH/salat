package de.hbt.salat.jira.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import de.hbt.salat.jira.domain.JiraWorklogSync;
import de.hbt.salat.order.domain.Customerorder;

/**
 * What SALAT has written to JIRA, found by the scope that wrote it — order and suborder, compared
 * by id (#1323); a {@code null} suborder is the whole order and compared as such.
 */
@Repository
public interface JiraWorklogSyncRepository extends JpaRepository<JiraWorklogSync, Long> {

  /**
   * What SALAT has written for this scope from the given day on (#1007). Bounded by the day rather
   * than read whole: rows before the configured start date belong to an earlier period the sync no
   * longer covers, and comparing them against sums that were never collected for them would read
   * as "all bookings of that day are gone" and delete worklogs nobody asked about.
   */
  @Query("""
      select w from JiraWorklogSync w
      where w.customerorder.id = :customerorderId
        and (w.suborder.id = :suborderId or (w.suborder is null and :suborderId is null))
        and w.workDate >= :from
      """)
  List<JiraWorklogSync> findInScopeFrom(long customerorderId, Long suborderId, LocalDate from);

  /** Takes the rows of a moved suborder branch along, like {@code JiraTicketRepository}. */
  @Modifying
  @Query("""
      update JiraWorklogSync w set w.customerorder = :customerorder
      where w.suborder.id in :suborderIds and w.customerorder <> :customerorder
      """)
  int moveBranchToCustomerorder(Collection<Long> suborderIds, Customerorder customerorder);

  @Modifying
  @Query("delete from JiraWorklogSync w where w.customerorder.id = :customerorderId")
  int deleteByCustomerorderId(long customerorderId);

  @Modifying
  @Query("delete from JiraWorklogSync w where w.suborder.id in :suborderIds")
  int deleteBySuborderIdIn(Collection<Long> suborderIds);
}
