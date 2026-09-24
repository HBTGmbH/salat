package org.tb.auth.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * One rule as the editor submits it (#1074). Lists, not sets: what somebody typed keeps its order until the entity
 * takes it, and a duplicate is dropped there rather than silently reordering the input on the way back to the form.
 */
public record AuthorizationRuleData(
    String category,
    List<String> granteeIds,
    List<String> objectIds,
    List<AccessLevel> accessLevels,
    LocalDate validFrom,
    LocalDate validUntil
) {

}
