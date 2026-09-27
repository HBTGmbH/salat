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

  Optional<JiraTicket> findByScopeSignAndJiraId(String scopeSign, long jiraId);

  List<JiraTicket> findByScopeSign(String scopeSign);

  List<JiraTicket> findByScopeSignAndKeyIn(String scopeSign, Collection<String> keys);

  List<JiraTicket> findByScopeSignAndParentKeyIn(String scopeSign, Collection<String> parentKeys);

  /**
   * The tickets of several scopes at once (#1092). The booking list offers the tickets of every order it may show, and
   * a ticket tree that stopped at a scope boundary would be a tree with holes.
   */
  List<JiraTicket> findByScopeSignIn(Collection<String> scopeSigns);


  /**
   * Suggestions for the booking form (#982). Matches the typed text against key and summary alike —
   * whoever books remembers either the number or what the ticket was about. Most recently updated
   * first, because that is what someone is booking on today.
   *
   * <p>Several scopes at once (#1025): booking on a suborder offers the tickets of its whole branch,
   * so the caller passes the scope of every level from that suborder up to the customer order. The
   * limit applies to the branch as a whole, not per scope, and the same key may well arrive from two
   * of them — de-duplicating is the caller's job.
   */
  @Query("""
      select t from JiraTicket t
      where t.scopeSign in :scopeSigns
        and (lower(t.key) like lower(concat('%', :searchTerm, '%'))
             or lower(t.summary) like lower(concat('%', :searchTerm, '%')))
      order by t.updatedTs desc nulls last, t.key asc
      """)
  List<JiraTicket> search(Collection<String> scopeSigns, String searchTerm, Pageable pageable);

}
