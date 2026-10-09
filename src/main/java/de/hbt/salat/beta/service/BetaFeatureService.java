package de.hbt.salat.beta.service;

import static de.hbt.salat.common.util.ClockProvider.now;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.beta.domain.BetaFeatures;
import de.hbt.salat.beta.domain.BetaParticipation;
import de.hbt.salat.beta.persistence.BetaEmployeeReferences;
import de.hbt.salat.beta.persistence.BetaParticipationRepository;
import de.hbt.salat.common.beta.BetaFeature;
import de.hbt.salat.settings.service.UserPreferenceService;

/**
 * The beta switches of the logged-in person, and — since #1447 — their participation: switching a
 * beta on or off is recorded with its time, so that the evaluation can tell how many tried it and
 * how many went back. A switch made while acting as somebody else is not recorded (
 * {@link MeasuredPerson}).
 */
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class BetaFeatureService {

  private final UserPreferenceService userPreferenceService;
  private final BetaFeatureRegistry registry;
  private final BetaParticipationRepository participationRepository;
  private final BetaEmployeeReferences employeeReferences;
  private final MeasuredPerson measuredPerson;

  @Transactional(readOnly = true)
  public BetaFeatures getForCurrentUser() {
    return current();
  }

  @Transactional(readOnly = true)
  public boolean isEnabledForCurrentUser(BetaFeature feature) {
    return current().has(feature.getKey());
  }

  /** The same by key, for a key that arrives from the browser; an unknown key is never on. */
  @Transactional(readOnly = true)
  public boolean isEnabledForCurrentUser(String featureKey) {
    return current().has(featureKey);
  }

  public void saveForCurrentUser(Collection<String> featureKeys) {
    var before = current();
    var after = BetaFeatures.ofKeys(featureKeys, registry::isKnown);
    save(after);
    recordSwitches(before, after);
  }

  /** Used by the in-context activation link, which switches on a single feature. */
  public void enableForCurrentUser(String featureKey) {
    var before = current();
    if (!registry.isKnown(featureKey) || before.has(featureKey)) {
      return;
    }
    var after = before.with(featureKey);
    save(after);
    recordSwitches(before, after);
  }

  private BetaFeatures current() {
    return BetaFeatures.from(userPreferenceService.getModuleSettings(BetaFeatures.MODULE_KEY), registry::isKnown);
  }

  private void save(BetaFeatures features) {
    userPreferenceService.saveModuleSettings(BetaFeatures.MODULE_KEY, features.toMap());
  }

  private void recordSwitches(BetaFeatures before, BetaFeatures after) {
    measuredPerson.employeeId().ifPresent(employeeId -> {
      after.keys().stream().filter(key -> !before.has(key)).forEach(key -> switchedOn(key, employeeId));
      before.keys().stream().filter(key -> !after.has(key)).forEach(key -> switchedOff(key, employeeId));
    });
  }

  private void switchedOn(String featureKey, long employeeId) {
    var participation = participationRepository.findOne(featureKey, employeeId).orElseGet(() -> {
      var created = new BetaParticipation();
      created.setFeatureKey(featureKey);
      created.setEmployee(employeeReferences.employee(employeeId));
      created.setFirstEnabledAt(now());
      return created;
    });
    participation.setEnabledAt(now());
    participation.setSwitchOffPending(false);
    participationRepository.save(participation);
  }

  /**
   * Switched off: asked why on the settings page next. Without a row the beta was switched on
   * while nothing was recorded — acting as somebody else — and there is no time to measure from.
   */
  private void switchedOff(String featureKey, long employeeId) {
    participationRepository.findOne(featureKey, employeeId).ifPresent(participation -> {
      participation.setDisabledAt(now());
      participation.setSwitchOffPending(true);
      participationRepository.save(participation);
    });
  }

}
