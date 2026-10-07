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
import de.hbt.salat.jira.domain.JiraTicketParentLink;
import de.hbt.salat.order.domain.Customerorder;

/**
 * The replicated tickets, found by the scope they were fetched under — the pair of customer order
 * and suborder ids (#1323). A {@code null} suborder means the whole order and is compared as such:
 * {@code t.suborder.id = :suborderId} alone would never match it.
 */
@Repository
public interface JiraTicketRepository extends JpaRepository<JiraTicket, Long> {

  /**
   * The ticket this replication maintains under the JIRA id (#1386). A JIRA id is unique per
   * replication, not per scope: two replications of one scope may read two JIRA instances, which hand
   * out the same numeric ids.
   */
  @Query("select t from JiraTicket t where t.replication.id = :replicationId and t.jiraId = :jiraId")
  Optional<JiraTicket> findMaintainedByJiraId(long replicationId, long jiraId);

  /**
   * The ticket of the scope that nobody maintains and carries the JIRA id (#1386) — imported with its
   * id, or left behind by a deleted replication. The first if several do.
   */
  @Query("""
      select t from JiraTicket t
      where t.customerorder.id = :customerorderId
        and (t.suborder.id = :suborderId or (t.suborder is null and :suborderId is null))
        and t.replication is null and t.jiraId = :jiraId
      order by t.id
      """)
  List<JiraTicket> findUnmaintainedInScopeByJiraId(long customerorderId, Long suborderId, long jiraId);

  @Query("""
      select t from JiraTicket t
      where t.customerorder.id = :customerorderId
        and (t.suborder.id = :suborderId or (t.suborder is null and :suborderId is null))
      """)
  List<JiraTicket> findInScope(long customerorderId, Long suborderId);

  /**
   * The tickets of these keys that the replication maintains (#1386) — its own, by the foreign key,
   * not every ticket of its scope: one maintained by hand or by another replication is not its
   * business.
   */
  @Query("select t from JiraTicket t where t.replication.id = :replicationId and t.key in :keys")
  List<JiraTicket> findMaintainedByKeyIn(long replicationId, Collection<String> keys);

  /** The ticket with this key in the scope, replicated or not — a key is unique per scope. */
  @Query("""
      select t from JiraTicket t
      where t.customerorder.id = :customerorderId
        and (t.suborder.id = :suborderId or (t.suborder is null and :suborderId is null))
        and t.key = :key
      """)
  Optional<JiraTicket> findInScopeByKey(long customerorderId, Long suborderId, String key);

  /**
   * The rows of the ticket page (#1386): the tickets of the given orders, or of every order — of every
   * scope, or of the given suborders — narrowed by key, title and type, sorted as the page asks. A
   * flag stands for "no restriction", because an empty {@code in} list is no portable way of saying
   * so; the list next to it is then ignored.
   */
  @Query("""
      select t from JiraTicket t left join fetch t.replication
      where (:allOrders = true or t.customerorder.id in :customerorderIds)
        and (:allScopes = true or t.suborder.id in :suborderIds)
        and (:allKeys = true or upper(t.key) in :keys)
        and (:title is null or lower(t.summary) like lower(concat('%', :title, '%')))
        and (:allTypes = true or t.issueType in :issueTypes)
      """)
  List<JiraTicket> findForTicketPage(boolean allOrders, Collection<Long> customerorderIds, boolean allScopes,
      Collection<Long> suborderIds, boolean allKeys, Collection<String> keys, String title, boolean allTypes,
      Collection<String> issueTypes, Pageable page);

  /**
   * The figures of the ticket page over every hit, per type: the type, all hits and the replicated
   * ones among them. Same conditions as {@link #findForTicketPage}.
   */
  @Query("""
      select t.issueType, count(t), count(t.replication) from JiraTicket t
      where (:allOrders = true or t.customerorder.id in :customerorderIds)
        and (:allScopes = true or t.suborder.id in :suborderIds)
        and (:allKeys = true or upper(t.key) in :keys)
        and (:title is null or lower(t.summary) like lower(concat('%', :title, '%')))
        and (:allTypes = true or t.issueType in :issueTypes)
      group by t.issueType
      """)
  List<Object[]> countForTicketPage(boolean allOrders, Collection<Long> customerorderIds, boolean allScopes,
      Collection<Long> suborderIds, boolean allKeys, Collection<String> keys, String title, boolean allTypes,
      Collection<String> issueTypes);

  /** The orders that have tickets at all, replicated or by hand — what the ticket page offers (#1386). */
  @Query("select distinct t.customerorder.id from JiraTicket t")
  List<Long> findCustomerorderIdsWithTickets();

  /** Whether the order has tickets at all, in any of its scopes (#1386). */
  @Query("select count(t) > 0 from JiraTicket t where t.customerorder.id = :customerorderId")
  boolean existsInCustomerorder(long customerorderId);

  /** The suborders of an order that have tickets of their own (#1386). */
  @Query("select distinct t.suborder.id from JiraTicket t where t.customerorder.id = :customerorderId and t.suborder is not null")
  List<Long> findSuborderIdsWithTickets(long customerorderId);

  /** Every type the tickets of these orders — or of every order — carry, for the type filter. */
  @Query("""
      select distinct t.issueType from JiraTicket t
      where (:allOrders = true or t.customerorder.id in :customerorderIds) and t.issueType is not null
      order by t.issueType
      """)
  List<String> findIssueTypes(boolean allOrders, Collection<Long> customerorderIds);

  /** Key and parent of every ticket of these orders — or of every order — for taking children along. */
  @Query("""
      select new de.hbt.salat.jira.domain.JiraTicketParentLink(t.key, t.parentKey) from JiraTicket t
      where (:allOrders = true or t.customerorder.id in :customerorderIds)
      """)
  List<JiraTicketParentLink> findParentLinks(boolean allOrders, Collection<Long> customerorderIds);

  /** The tickets of the order with this key — one per scope at most — for jumping to a parent. */
  @Query("select t from JiraTicket t where t.customerorder.id = :customerorderId and t.key = :key")
  List<JiraTicket> findInCustomerorderByKey(long customerorderId, String key);

  /** The tickets of the same scope naming this key as parent, for the detail page. */
  @Query("""
      select t from JiraTicket t left join fetch t.replication
      where t.customerorder.id = :customerorderId
        and (t.suborder.id = :suborderId or (t.suborder is null and :suborderId is null))
        and t.parentKey = :key
      order by t.key
      """)
  List<JiraTicket> findChildrenInScope(long customerorderId, Long suborderId, String key);


  /**
   * The tickets of several scopes at once (#1092): the order-wide ones of these orders and those of
   * these suborders. The booking list offers the tickets of every order it may show, and a ticket
   * tree that stopped at a scope boundary would be a tree with holes.
   */
  @Query("""
      select t from JiraTicket t
      where (t.suborder is null and t.customerorder.id in :customerorderIds)
         or t.suborder.id in :suborderIds
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
   *
   * <p>The order-wide tickets and those of the suborders are two reads, joined with {@code union all}
   * (#1402). One condition with {@code or} over both kinds of scope reads no index, and MySQL scanned
   * the whole table for it; each half on its own reads an index — the order-wide half one that starts
   * with the order, the suborder half the one of its foreign key. Each half brings the ids of its own
   * first {@code :limit} rows, which together contain the first {@code :limit} of the branch; the
   * outer query loads only those, puts them in order once more and cuts them to the limit.
   */
  @Query("""
      select t from JiraTicket t
      join (
        (select orderWide.id as id from JiraTicket orderWide
         where orderWide.suborder is null and orderWide.customerorder.id = :customerorderId
           and (lower(orderWide.key) like lower(concat('%', :searchTerm, '%'))
                or lower(orderWide.summary) like lower(concat('%', :searchTerm, '%')))
         order by orderWide.updatedTs desc nulls last, orderWide.key asc
         limit :limit)
        union all
        (select ofSuborder.id as id from JiraTicket ofSuborder
         where ofSuborder.suborder.id in :suborderIds
           and (lower(ofSuborder.key) like lower(concat('%', :searchTerm, '%'))
                or lower(ofSuborder.summary) like lower(concat('%', :searchTerm, '%')))
         order by ofSuborder.updatedTs desc nulls last, ofSuborder.key asc
         limit :limit)
      ) hit on hit.id = t.id
      order by t.updatedTs desc nulls last, t.key asc
      limit :limit
      """)
  List<JiraTicket> search(long customerorderId, Collection<Long> suborderIds, String searchTerm, int limit);

  /**
   * Takes the tickets of a suborder branch along to the customer order it was moved to (#1323).
   * Their scope is the pair of order and suborder, and a moved suborder means its new place in the
   * tree. Rows already on that order are left alone, so a save that moves nothing writes nothing.
   */
  @Modifying
  @Query("""
      update JiraTicket t set t.customerorder = :customerorder
      where t.suborder.id in :suborderIds and t.customerorder <> :customerorder
      """)
  int moveBranchToCustomerorder(Collection<Long> suborderIds, Customerorder customerorder);

  /**
   * Releases the tickets of a replication that moves to another scope (#1386): they stay where they
   * are, maintained by nobody, as if the replication had been deleted — editable by hand, and taken
   * over by whichever replication of their scope delivers their key next.
   */
  @Modifying
  @Query("update JiraTicket t set t.replication = null where t.replication.id = :replicationId")
  int releaseFromReplication(long replicationId);

  @Modifying
  @Query("delete from JiraTicket t where t.customerorder.id = :customerorderId")
  int deleteByCustomerorderId(long customerorderId);

  @Modifying
  @Query("delete from JiraTicket t where t.suborder.id in :suborderIds")
  int deleteBySuborderIdIn(Collection<Long> suborderIds);

}
