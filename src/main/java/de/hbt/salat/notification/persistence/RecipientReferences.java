package de.hbt.salat.notification.persistence;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.SalatUser;

/**
 * References to the login a notification is for, whose id arrives from the sender (#1370).
 *
 * <p>A reference rather than the loaded entity: it only carries the id into the foreign key and
 * loads nothing. Whether the login exists the foreign key decides — like the id it replaces.
 */
@Component
@RequiredArgsConstructor
public class RecipientReferences {

  private final EntityManager entityManager;

  public SalatUser salatUser(long salatUserId) {
    return entityManager.getReference(SalatUser.class, salatUserId);
  }
}
