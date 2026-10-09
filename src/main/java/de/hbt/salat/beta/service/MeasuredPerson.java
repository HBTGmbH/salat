package de.hbt.salat.beta.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.employee.domain.AuthorizedEmployee;

/**
 * The person whose use of a beta is measured (#1447): the logged-in person, and nobody while they
 * act as somebody else. The switches are a preference of the real login, but
 * {@link AuthorizedEmployee} follows the impersonation — counting then would credit the other
 * person with a use they did not make, and ask them about it. Empty outside a request as well.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MeasuredPerson {

  private final AuthorizedUser authorizedUser;
  private final AuthorizedEmployee authorizedEmployee;

  Optional<Long> employeeId() {
    try {
      if (authorizedUser.getImpersonateLoginSign() != null) {
        return Optional.empty();
      }
      return Optional.ofNullable(authorizedEmployee.getEmployeeId());
    } catch (RuntimeException e) {
      // no request scope, e.g. in a job: nothing to measure
      log.debug("No person to measure a beta for", e);
      return Optional.empty();
    }
  }
}
