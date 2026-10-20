package de.hbt.salat.beta.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import de.hbt.salat.beta.domain.AnsweredBeta;
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

  /**
   * The betas each person answered the question about, with the day of the last change of the
   * participation, ordered by it (#1501). The answer itself stays without the person (ADR-0039);
   * this says only that there was one.
   */
  @Query("""
      select new de.hbt.salat.beta.domain.AnsweredBeta(p.employee.id, p.featureKey, cast(p.lastupdate as LocalDate))
      from BetaParticipation p
      where p.feedbackState = de.hbt.salat.beta.domain.FeedbackState.ANSWERED
      order by p.employee.id, p.lastupdate
      """)
  List<AnsweredBeta> findAnswered();

  @Query("""
      select count(p) from BetaParticipation p
      where p.employee.id = :employeeId and p.feedbackState = de.hbt.salat.beta.domain.FeedbackState.ANSWERED
      """)
  long countAnswered(@Param("employeeId") long employeeId);
}
