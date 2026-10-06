package de.hbt.salat.jira.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import de.hbt.salat.jira.domain.JiraTicketImport;

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
}
