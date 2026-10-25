package de.hbt.salat.common.persistence;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import de.hbt.salat.common.domain.IdempotencyEntry;

public interface IdempotencyEntryRepository extends CrudRepository<IdempotencyEntry, Long> {

  Optional<IdempotencyEntry> findByUserIdAndIdempotencyKey(long userId, String idempotencyKey);

  @Modifying
  @Query("delete from IdempotencyEntry e where e.created < :cutoff")
  int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
