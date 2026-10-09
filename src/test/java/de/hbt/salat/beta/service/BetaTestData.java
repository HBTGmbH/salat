package de.hbt.salat.beta.service;

import static de.hbt.salat.testutils.ReferenceTestUtils.employeeWithId;

import java.time.LocalDateTime;
import de.hbt.salat.beta.domain.BetaParticipation;
import de.hbt.salat.common.beta.BetaFeature;

/** A beta as a module would contribute it (#1447). */
final class BetaTestData {

  static final String KEY = "test-beta";
  /** An event as a module would count it; the beta module knows none of its own. */
  static final String EVENT = "favorite-applied";
  static final long EMPLOYEE_ID = 42L;
  static final BetaFeature FEATURE = new BetaFeature(KEY, 20);

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
