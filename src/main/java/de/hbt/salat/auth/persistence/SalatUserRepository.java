package de.hbt.salat.auth.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import de.hbt.salat.auth.domain.SalatUser;

@Repository
public interface SalatUserRepository extends CrudRepository<SalatUser, Long> {

  Optional<SalatUser> findByLoginname(String loginname);

  @Query("select u.id from SalatUser u where u.loginname = :loginname")
  List<Long> findIdsByLoginname(String loginname);

}
