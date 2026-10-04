package de.hbt.salat.jira.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import de.hbt.salat.jira.domain.JiraWorklogSync;

/**
 * What SALAT has written to JIRA, found by the scope that wrote it — order and suborder by id
 * (#1323); a {@code null} suborder is the whole order and compared as such.
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
      where w.customerorderId = :customerorderId
        and (w.suborderId = :suborderId or (w.suborderId is null and :suborderId is null))
        and w.workDate >= :from
      """)
  List<JiraWorklogSync> findInScopeFrom(long customerorderId, Long suborderId, LocalDate from);

  /** The suborders of this order that carry worklogs of their own (#1323). */
  @Query("""
      select distinct w.suborderId from JiraWorklogSync w
      where w.customerorderId = :customerorderId and w.suborderId is not null
      """)
  List<Long> findSuborderIdsOfCustomerorder(long customerorderId);

  /** Those of these suborders that carry worklogs of their own (#1323). */
  @Query("select distinct w.suborderId from JiraWorklogSync w where w.suborderId in :suborderIds")
  List<Long> findSuborderIdsIn(Collection<Long> suborderIds);

  /** Keeps the sign column of the order-wide rows in step with the order (#1323). */
  @Modifying
  @Query("""
      update JiraWorklogSync w set w.scopeSign = :sign
      where w.customerorderId = :customerorderId and w.suborderId is null and w.scopeSign <> :sign
      """)
  int mirrorOrderWide(long customerorderId, String sign);

  /** Same for the rows of one suborder — including the order, should it have been moved there. */
  @Modifying
  @Query("""
      update JiraWorklogSync w set w.customerorderId = :customerorderId, w.scopeSign = :sign
      where w.suborderId = :suborderId and (w.scopeSign <> :sign or w.customerorderId <> :customerorderId)
      """)
  int mirrorSuborder(long suborderId, long customerorderId, String sign);

  @Modifying
  @Query("delete from JiraWorklogSync w where w.customerorderId = :customerorderId")
  int deleteByCustomerorderId(long customerorderId);

  @Modifying
  @Query("delete from JiraWorklogSync w where w.suborderId in :suborderIds")
  int deleteBySuborderIdIn(Collection<Long> suborderIds);
}
