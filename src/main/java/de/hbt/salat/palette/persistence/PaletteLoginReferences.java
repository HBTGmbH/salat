package de.hbt.salat.palette.persistence;

import de.hbt.salat.auth.domain.SalatUser;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The login a use of the palette belongs to, as a reference (ADR-0036); the id is the logged-in person's. */
@Component
@RequiredArgsConstructor
public class PaletteLoginReferences {

  private final EntityManager entityManager;

  public SalatUser salatUser(long salatUserId) {
    return entityManager.getReference(SalatUser.class, salatUserId);
  }
}
