package de.hbt.salat.settings.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.settings.domain.UserPreference;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

  Optional<UserPreference> findBySalatUser(SalatUser salatUser);

  Optional<UserPreference> findBySalatUserId(long salatUserId);

}
