package org.tb.settings.persistence;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.auth.domain.SalatUser;
import org.tb.settings.domain.UserPreference;

@Repository
public interface UserPreferenceRepository extends CrudRepository<UserPreference, Long> {

  Optional<UserPreference> findBySalatUser(SalatUser salatUser);

  Optional<UserPreference> findBySalatUserId(long salatUserId);

}
