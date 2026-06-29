package org.tb.settings.service;

import static org.tb.common.exception.ErrorCode.SE_USER_NOT_FOUND;

import java.time.LocalTime;
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
import org.tb.settings.domain.UserSettings;
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
  public UserPreference getOrCreateForCurrentUser() {
    SalatUser user = currentSalatUser();
    return repository.findBySalatUser(user)
        .orElseGet(() -> newPreferenceFor(user));
  }

  @Transactional(readOnly = true)
  public UserSettings getSettingsFor(SalatUser salatUser) {
    return repository.findBySalatUser(salatUser)
        .map(UserPreference::getSettings)
        .orElse(UserSettings.defaults());
  }

  @Transactional(readOnly = true)
  public UserSettings getSettingsFor(long salatUserId) {
    return repository.findBySalatUserId(salatUserId)
        .map(UserPreference::getSettings)
        .orElse(UserSettings.defaults());
  }

  @Transactional(readOnly = true)
  public UserSettings getSettingsFor(String loginName) {
    SalatUser user = salatUserRepository.findByLoginname(loginName)
        .orElseThrow(() -> new InvalidDataException(SE_USER_NOT_FOUND));
    return getSettingsFor(user);
  }

  // Backward compatibility: returns work day start from settings
  @Transactional(readOnly = true)
  public LocalTime getWorkDayStart() {
    SalatUser user = currentSalatUser();
    return getSettingsFor(user).workDayStart();
  }

  public void saveSettings(SalatUser salatUser, UserSettings settings) {
    UserPreference pref = repository.findBySalatUser(salatUser)
        .orElseGet(() -> newPreferenceFor(salatUser));
    pref.setSettings(settings);
    repository.save(pref);
  }

  // Backward compatibility
  public void saveForCurrentUser(LocalTime workDayStart) {
    SalatUser user = currentSalatUser();
    UserSettings settings = new UserSettings(workDayStart);
    saveSettings(user, settings);
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
