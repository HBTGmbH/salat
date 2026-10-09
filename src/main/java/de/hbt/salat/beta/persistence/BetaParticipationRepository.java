package de.hbt.salat.beta.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import de.hbt.salat.beta.domain.BetaParticipation;

@Repository
public interface BetaParticipationRepository extends JpaRepository<BetaParticipation, Long> {

  @Query("select p from BetaParticipation p where p.featureKey = :featureKey and p.employee.id = :employeeId")
  Optional<BetaParticipation> findOne(@Param("featureKey") String featureKey, @Param("employeeId") long employeeId);

  @Query("select p from BetaParticipation p where p.employee.id = :employeeId and p.switchOffPending = true")
  List<BetaParticipation> findSwitchOffPending(@Param("employeeId") long employeeId);

  List<BetaParticipation> findAllByFeatureKey(String featureKey);

  @Query("select distinct p.featureKey from BetaParticipation p")
  List<String> findFeatureKeys();
}
