package de.hbt.salat.jira.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import de.hbt.salat.jira.domain.JiraTicketImport;
import de.hbt.salat.order.domain.Customerorder;

@Repository
public interface JiraTicketImportRepository extends JpaRepository<JiraTicketImport, Long> {

  /** The imports of exactly this scope, the latest first (#1386); a page of one is the latest. */
  @Query("""
      select i from JiraTicketImport i
      where i.customerorder.id = :customerorderId
        and (i.suborder.id = :suborderId or (i.suborder is null and :suborderId is null))
      order by i.id desc
      """)
  List<JiraTicketImport> findLatestInScope(long customerorderId, Long suborderId, Pageable page);

  /** The imports of an order in any of its scopes, the latest first (#1386). */
  @Query("select i from JiraTicketImport i where i.customerorder.id = :customerorderId order by i.id desc")
  List<JiraTicketImport> findLatestInCustomerorder(long customerorderId, Pageable page);

  /**
   * The imports of these orders — or of every order — the latest first (#1386), for a file of the same
   * shape imported elsewhere before.
   */
  @Query("""
      select i from JiraTicketImport i
      where (:allOrders = true or i.customerorder.id in :customerorderIds)
      order by i.id desc
      """)
  List<JiraTicketImport> findLatest(boolean allOrders, Collection<Long> customerorderIds, Pageable page);

  /**
   * Takes the imports of a suborder branch along to the customer order it was moved to, like its
   * tickets (#1323): the latest import of a scope supplies inherited fields and the column reading
   * the preview proposes, and is looked up by order and suborder.
   */
  @Modifying
  @Query("""
      update JiraTicketImport i set i.customerorder = :customerorder
      where i.suborder.id in :suborderIds and i.customerorder <> :customerorder
      """)
  int moveBranchToCustomerorder(Collection<Long> suborderIds, Customerorder customerorder);

  @Modifying
  @Query("delete from JiraTicketImport i where i.customerorder.id = :customerorderId")
  int deleteByCustomerorderId(long customerorderId);

  @Modifying
  @Query("delete from JiraTicketImport i where i.suborder.id in :suborderIds")
  int deleteBySuborderIdIn(Collection<Long> suborderIds);
}
