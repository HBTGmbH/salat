package org.tb.jira.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tb.jira.domain.JiraWorklogSync;

@Repository
public interface JiraWorklogSyncRepository extends JpaRepository<JiraWorklogSync, Long> {

  /**
   * What SALAT has written for this scope from the given day on (#1007). Bounded by the day rather
   * than read whole: rows before the configured start date belong to an earlier period the sync no
   * longer covers, and comparing them against sums that were never collected for them would read
   * as "all bookings of that day are gone" and delete worklogs nobody asked about.
   */
  List<JiraWorklogSync> findByScopeSignAndWorkDateGreaterThanEqual(String scopeSign, LocalDate from);
}
