package de.hbt.salat.beta.service;

import static de.hbt.salat.testutils.ReferenceTestUtils.employeeWithId;

import java.time.LocalDateTime;
import java.util.Set;
import de.hbt.salat.beta.domain.BetaDefinition;
import de.hbt.salat.beta.domain.BetaParticipation;

/** A stand-in beta while {@code BetaFeature} is empty (#1447). */
final class BetaTestData {

  static final String KEY = "test-beta";
  static final String EVENT = "applied";
  static final long EMPLOYEE_ID = 42L;
  static final BetaDefinition DEFINITION = new BetaDefinition(KEY, Set.of(EVENT), 20);

  private BetaTestData() {
  }

  static BetaParticipation participation(LocalDateTime enabledAt) {
    var participation = new BetaParticipation();
    participation.setFeatureKey(KEY);
    participation.setEmployee(employeeWithId(EMPLOYEE_ID));
    participation.setFirstEnabledAt(enabledAt);
    participation.setEnabledAt(enabledAt);
    return participation;
  }
}
