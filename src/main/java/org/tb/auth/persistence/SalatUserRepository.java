package org.tb.auth.persistence;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.tb.auth.domain.SalatUser;

@Repository
public interface SalatUserRepository extends CrudRepository<SalatUser, Long> {

  Optional<SalatUser> findByLoginname(String loginname);

}
