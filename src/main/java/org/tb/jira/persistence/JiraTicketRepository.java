package org.tb.jira.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.tb.jira.domain.JiraTicket;

@Repository
public interface JiraTicketRepository extends JpaRepository<JiraTicket, Long> {

  Optional<JiraTicket> findByCustomerorderSignAndJiraId(String customerorderSign, long jiraId);

  @Query("select max(t.updatedTs) from JiraTicket t where t.customerorderSign = :customerorderSign")
  LocalDateTime findMaxUpdatedTs(String customerorderSign);

  List<JiraTicket> findByCustomerorderSignAndKeyIn(String customerorderSign, Collection<String> keys);

  List<JiraTicket> findByCustomerorderSignAndParentKeyIn(String customerorderSign, Collection<String> parentKeys);

}
