package de.hbt.salat.beta.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import de.hbt.salat.beta.domain.BetaUsage;
import de.hbt.salat.beta.domain.BetaUsageRow;
import de.hbt.salat.beta.domain.BetaVariant;

@Repository
public interface BetaUsageRepository extends JpaRepository<BetaUsage, Long> {

  /** Raises the count of the day's row by one; 0 when there is no row yet (#1447). */
  @Modifying
  @Query("""
      update BetaUsage u set u.useCount = u.useCount + 1
       where u.featureKey = :featureKey and u.eventKey = :eventKey and u.employee.id = :employeeId
         and u.usageDate = :usageDate and u.variant = :variant
      """)
  int increment(@Param("featureKey") String featureKey, @Param("eventKey") String eventKey,
      @Param("employeeId") long employeeId, @Param("usageDate") LocalDate usageDate,
      @Param("variant") BetaVariant variant);

  /** How often the person used the beta while it was switched on. */
  @Query("""
      select coalesce(sum(u.useCount), 0) from BetaUsage u
       where u.featureKey = :featureKey and u.employee.id = :employeeId
         and u.variant = de.hbt.salat.beta.domain.BetaVariant.BETA
      """)
  long sumUsesWithBeta(@Param("featureKey") String featureKey, @Param("employeeId") long employeeId);

  @Query("""
      select new de.hbt.salat.beta.domain.BetaUsageRow(u.eventKey, u.employee.id, u.usageDate, u.variant, u.useCount)
        from BetaUsage u
       where u.featureKey = :featureKey and u.usageDate >= :from
      """)
  List<BetaUsageRow> findRows(@Param("featureKey") String featureKey, @Param("from") LocalDate from);

  @Query("select distinct u.featureKey from BetaUsage u")
  List<String> findFeatureKeys();
}
