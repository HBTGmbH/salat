package de.hbt.salat.secret.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import de.hbt.salat.secret.domain.Secret;

@Repository
public interface SecretRepository extends JpaRepository<Secret, Long> {

  /** The secrets a key other than the active one has encrypted — they are encrypted anew at start. */
  @Query("select s.id from Secret s where s.keyId <> :keyId order by s.id")
  List<Long> findIdsNotEncryptedWith(@Param("keyId") String keyId);
}
