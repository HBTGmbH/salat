package de.hbt.salat.reporting.persistence;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.SalatUser;

/**
 * References to the login that owns a report or a scheduled job, whose id comes from the login the
 * record is created under (#1370).
 *
 * <p>A reference rather than the loaded entity: it only carries the id into the foreign key and
 * loads nothing. {@code null} for {@code null} — a record without an owner belongs to nobody.
 */
@Component
@RequiredArgsConstructor
public class OwnerReferences {

  private final EntityManager entityManager;

  public SalatUser salatUser(Long salatUserId) {
    return salatUserId == null ? null : entityManager.getReference(SalatUser.class, salatUserId);
  }
}
