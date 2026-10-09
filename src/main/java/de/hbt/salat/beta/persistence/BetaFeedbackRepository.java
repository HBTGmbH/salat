package de.hbt.salat.beta.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import de.hbt.salat.beta.domain.BetaFeedback;

@Repository
public interface BetaFeedbackRepository extends JpaRepository<BetaFeedback, Long> {

  List<BetaFeedback> findAllByFeatureKey(String featureKey);

  @Query("select distinct f.featureKey from BetaFeedback f")
  List<String> findFeatureKeys();
}
