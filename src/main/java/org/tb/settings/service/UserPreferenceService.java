package org.tb.settings.service;

import static org.tb.common.exception.ErrorCode.SE_USER_NOT_FOUND;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.exception.InvalidDataException;
import org.tb.settings.domain.UserPreference;
import org.tb.settings.domain.UserPreferenceMap;
import org.tb.settings.persistence.UserPreferenceRepository;

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
