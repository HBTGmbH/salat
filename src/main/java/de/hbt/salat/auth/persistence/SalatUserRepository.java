package de.hbt.salat.auth.persistence;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.auth.domain.SalatUser;

@Repository
public interface SalatUserRepository extends CrudRepository<SalatUser, Long> {

  Optional<SalatUser> findByLoginname(String loginname);

}
