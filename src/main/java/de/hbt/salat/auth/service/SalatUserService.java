package de.hbt.salat.auth.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.SalatUserRepository;

/**
 * Creates, changes and deletes logins (#1460). A login belongs to this module; another module that
 * keeps one — a person in {@code employee} — hands over the values and refers to the login by its id
 * (ADR-0036, rule 3: a reference across the module boundary is read only).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SalatUserService {

  private final SalatUserRepository salatUserRepository;

  /** @return the id of the new login */
  @Authorized(requiresManager = true)
  public long createSalatUser(String loginname, String status) {
    var salatUser = new SalatUser();
    salatUser.setLoginname(loginname);
    salatUser.setStatus(status);
    return salatUserRepository.save(salatUser).getId();
  }

  @Authorized(requiresManager = true)
  public void updateSalatUser(long salatUserId, String loginname, String status) {
    var salatUser = salatUserById(salatUserId);
    salatUser.setLoginname(loginname);
    salatUser.setStatus(status);
  }

  /** A new login name, the status stays — for anonymizing a person. */
  @Authorized(requiresManager = true)
  public void renameSalatUser(long salatUserId, String loginname) {
    salatUserById(salatUserId).setLoginname(loginname);
  }

  @Authorized(requiresManager = true)
  public void deleteSalatUser(long salatUserId) {
    salatUserRepository.deleteById(salatUserId);
  }

  /** The id of the login with exactly this name, empty without one. */
  @Transactional(readOnly = true)
  public Optional<Long> getSalatUserIdByLoginname(String loginname) {
    return salatUserRepository.findByLoginname(loginname).map(SalatUser::getId);
  }

  /** The callers hold the id from a person who refers to the login, so a missing one is inconsistent data. */
  private SalatUser salatUserById(long salatUserId) {
    return salatUserRepository.findById(salatUserId)
        .orElseThrow(() -> new IllegalStateException("no login with id " + salatUserId));
  }
}
