package de.hbt.salat.settings.service;

import static de.hbt.salat.common.exception.ErrorCode.SE_USER_NOT_FOUND;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.settings.domain.UserPreference;
import de.hbt.salat.settings.domain.UserPreferenceMap;
import de.hbt.salat.settings.persistence.UserPreferenceRepository;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class UserPreferenceService {

  private final UserPreferenceRepository repository;
  private final SalatUserRepository salatUserRepository;
  private final AuthorizedUser authorizedUser;

  @Transactional(readOnly = true)
  public Map<String, Object> getModuleSettings(String moduleKey) {
    return getOrCreateForCurrentUser().getSettings().getModule(moduleKey);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getModuleSettings(SalatUser user, String moduleKey) {
    return repository.findBySalatUser(user)
        .map(p -> p.getSettings().getModule(moduleKey))
        .orElse(Map.of());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getModuleSettings(long salatUserId, String moduleKey) {
    return repository.findBySalatUserId(salatUserId)
        .map(p -> p.getSettings().getModule(moduleKey))
        .orElse(Map.of());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getModuleSettings(String loginName, String moduleKey) {
    SalatUser user = salatUserRepository.findByLoginname(loginName)
        .orElseThrow(() -> new InvalidDataException(SE_USER_NOT_FOUND));
    return getModuleSettings(user, moduleKey);
  }

  public void saveModuleSettings(String moduleKey, Map<String, Object> settings) {
    UserPreference pref = getOrCreateForCurrentUser();
    pref.setSettings(pref.getSettings().withModule(moduleKey, settings));
    repository.save(pref);
  }

  UserPreference getOrCreateForCurrentUser() {
    SalatUser user = currentSalatUser();
    return repository.findBySalatUser(user)
        .orElseGet(() -> newPreferenceFor(user));
  }

  private SalatUser currentSalatUser() {
    return salatUserRepository.findByLoginname(authorizedUser.getLoginSign())
        .orElseThrow(() -> new InvalidDataException(SE_USER_NOT_FOUND));
  }

  private UserPreference newPreferenceFor(SalatUser user) {
    UserPreference p = new UserPreference();
    p.setSalatUser(user);
    return p;
  }

}
