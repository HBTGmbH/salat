package org.tb.settings.service;

import static org.tb.common.GlobalConstants.DEFAULT_WORK_DAY_START;
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
  public LocalTime getWorkDayStart() {
    SalatUser user = currentSalatUser();
    return repository.findBySalatUser(user)
        .map(UserPreference::getWorkDayStart)
        .orElse(LocalTime.of(DEFAULT_WORK_DAY_START, 0));
  }

  public void saveForCurrentUser(LocalTime workDayStart) {
    SalatUser user = currentSalatUser();
    UserPreference pref = repository.findBySalatUser(user)
        .orElseGet(() -> newPreferenceFor(user));
    pref.setWorkDayStart(workDayStart);
    repository.save(pref);
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
