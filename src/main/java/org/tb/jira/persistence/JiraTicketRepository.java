package org.tb.jira.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tb.jira.domain.JiraTicket;

@Repository
public interface JiraTicketRepository extends JpaRepository<JiraTicket, Long> {

  Optional<JiraTicket> findByCustomerorderSignAndJiraId(String customerorderSign, long jiraId);

  List<JiraTicket> findByCustomerorderSignAndKeyIn(String customerorderSign, Collection<String> keys);

  List<JiraTicket> findByCustomerorderSignAndParentKeyIn(String customerorderSign, Collection<String> parentKeys);

}
