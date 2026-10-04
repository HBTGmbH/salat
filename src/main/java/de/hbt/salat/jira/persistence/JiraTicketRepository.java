package de.hbt.salat.jira.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import de.hbt.salat.jira.domain.JiraTicket;

/**
 * The replicated tickets, found by the scope they were fetched under — the pair of customer order
 * and suborder ids (#1323). A {@code null} suborder means the whole order and is compared as such:
 * {@code t.suborderId = :suborderId} alone would never match it.
 */
@Repository
public interface JiraTicketRepository extends JpaRepository<JiraTicket, Long> {

  @Query("""
      select t from JiraTicket t
      where t.customerorderId = :customerorderId
        and (t.suborderId = :suborderId or (t.suborderId is null and :suborderId is null))
        and t.jiraId = :jiraId
      """)
  Optional<JiraTicket> findInScopeByJiraId(long customerorderId, Long suborderId, long jiraId);

  @Query("""
      select t from JiraTicket t
      where t.customerorderId = :customerorderId
        and (t.suborderId = :suborderId or (t.suborderId is null and :suborderId is null))
      """)
  List<JiraTicket> findInScope(long customerorderId, Long suborderId);

  @Query("""
      select t from JiraTicket t
      where t.customerorderId = :customerorderId
        and (t.suborderId = :suborderId or (t.suborderId is null and :suborderId is null))
        and t.key in :keys
      """)
  List<JiraTicket> findInScopeByKeyIn(long customerorderId, Long suborderId, Collection<String> keys);

  /**
   * The tickets of several scopes at once (#1092): the order-wide ones of these orders and those of
   * these suborders. The booking list offers the tickets of every order it may show, and a ticket
   * tree that stopped at a scope boundary would be a tree with holes.
   */
  @Query("""
      select t from JiraTicket t
      where (t.suborderId is null and t.customerorderId in :customerorderIds)
         or t.suborderId in :suborderIds
      """)
  List<JiraTicket> findInScopes(Collection<Long> customerorderIds, Collection<Long> suborderIds);

  /**
   * Suggestions for the booking form (#982). Matches the typed text against key and summary alike —
   * whoever books remembers either the number or what the ticket was about. Most recently updated
   * first, because that is what someone is booking on today.
   *
   * <p>Several scopes at once (#1025): booking on a suborder offers the tickets of its whole branch,
   * so the caller passes the order and the suborders from the top level down to the one booked on.
   * The limit applies to the branch as a whole, not per scope, and the same key may well arrive from
   * two of them — de-duplicating is the caller's job.
   */
  @Query("""
      select t from JiraTicket t
      where ((t.suborderId is null and t.customerorderId = :customerorderId) or t.suborderId in :suborderIds)
        and (lower(t.key) like lower(concat('%', :searchTerm, '%'))
             or lower(t.summary) like lower(concat('%', :searchTerm, '%')))
      order by t.updatedTs desc nulls last, t.key asc
      """)
  List<JiraTicket> search(long customerorderId, Collection<Long> suborderIds, String searchTerm, Pageable pageable);

  /** The suborders of this order that carry tickets of their own (#1323). */
  @Query("""
      select distinct t.suborderId from JiraTicket t
      where t.customerorderId = :customerorderId and t.suborderId is not null
      """)
  List<Long> findSuborderIdsOfCustomerorder(long customerorderId);

  /** Those of these suborders that carry tickets of their own (#1323). */
  @Query("select distinct t.suborderId from JiraTicket t where t.suborderId in :suborderIds")
  List<Long> findSuborderIdsIn(Collection<Long> suborderIds);

  /**
   * Keeps the sign column of the order-wide tickets in step with the order (#1323). Rows that
   * already carry the sign are left alone, so a save that renames nothing writes nothing.
   */
  @Modifying
  @Query("""
      update JiraTicket t set t.scopeSign = :sign
      where t.customerorderId = :customerorderId and t.suborderId is null and t.scopeSign <> :sign
      """)
  int mirrorOrderWide(long customerorderId, String sign);

  /** Same for the tickets of one suborder — including the order, should it have been moved there. */
  @Modifying
  @Query("""
      update JiraTicket t set t.customerorderId = :customerorderId, t.scopeSign = :sign
      where t.suborderId = :suborderId and (t.scopeSign <> :sign or t.customerorderId <> :customerorderId)
      """)
  int mirrorSuborder(long suborderId, long customerorderId, String sign);

  @Modifying
  @Query("delete from JiraTicket t where t.customerorderId = :customerorderId")
  int deleteByCustomerorderId(long customerorderId);

  @Modifying
  @Query("delete from JiraTicket t where t.suborderId in :suborderIds")
  int deleteBySuborderIdIn(Collection<Long> suborderIds);

}
