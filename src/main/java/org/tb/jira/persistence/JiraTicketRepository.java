package org.tb.jira.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.tb.jira.domain.JiraTicket;

@Repository
public interface JiraTicketRepository extends JpaRepository<JiraTicket, Long> {

  Optional<JiraTicket> findByCustomerorderSignAndJiraId(String customerorderSign, long jiraId);

  List<JiraTicket> findByCustomerorderSign(String customerorderSign);

  List<JiraTicket> findByCustomerorderSignAndKeyIn(String customerorderSign, Collection<String> keys);

  List<JiraTicket> findByCustomerorderSignAndParentKeyIn(String customerorderSign, Collection<String> parentKeys);

  /**
   * Suggestions for the booking form (#982). Matches the typed text against key and summary alike —
   * whoever books remembers either the number or what the ticket was about. Most recently updated
   * first, because that is what someone is booking on today.
   */
  @Query("""
      select t from JiraTicket t
      where t.customerorderSign = :customerorderSign
        and (lower(t.key) like lower(concat('%', :searchTerm, '%'))
             or lower(t.summary) like lower(concat('%', :searchTerm, '%')))
      order by t.updatedTs desc nulls last, t.key asc
      """)
  List<JiraTicket> search(String customerorderSign, String searchTerm, Pageable pageable);

}
