package de.hbt.salat.employee.persistence;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.SalatUser;

/**
 * References to the login of a person, whose id the auth module hands back when it creates the login
 * (#1460).
 *
 * <p>A reference rather than the loaded entity: the person only refers to the login, it never writes
 * it (ADR-0036, rule 3). Creating, changing and deleting a login is {@code SalatUserService}'s.
 */
@Component
@RequiredArgsConstructor
public class SalatUserReferences {

  private final EntityManager entityManager;

  public SalatUser salatUser(long salatUserId) {
    return entityManager.getReference(SalatUser.class, salatUserId);
  }
}
