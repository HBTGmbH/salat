package org.tb.settings.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tb.auth.domain.SalatUser;
import org.tb.settings.domain.UserPreference;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

  Optional<UserPreference> findBySalatUser(SalatUser salatUser);

  Optional<UserPreference> findBySalatUserId(long salatUserId);

}
