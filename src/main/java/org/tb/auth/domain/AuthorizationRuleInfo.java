package org.tb.auth.domain;

import java.time.LocalDate;
import java.util.List;
import org.tb.common.util.DateUtils;

/**
 * One rule as the list and the form show it (#1074).
 *
 * @param categoryLabelKey message key of the category, or {@code null} where no module offers this category — such a
 *                         rule stays visible with its raw category rather than being quietly left out
 */
public record AuthorizationRuleInfo(
    Long id,
    String category,
    String categoryLabelKey,
    List<String> granteeIds,
    List<String> objectIds,
    List<AccessLevel> accessLevels,
    LocalDate validFrom,
    LocalDate validUntil
) {

  /** Whether the rule grants anything today — an ended rule stays in the list, greyed out. */
  public boolean isActive() {
    var today = DateUtils.today();
    return (validFrom == null || !validFrom.isAfter(today))
        && (validUntil == null || !validUntil.isBefore(today));
  }

}
