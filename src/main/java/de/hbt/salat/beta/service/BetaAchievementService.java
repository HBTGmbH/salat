package de.hbt.salat.beta.service;

import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static java.util.stream.Collectors.groupingBy;

import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.beta.domain.AnsweredBeta;
import de.hbt.salat.beta.persistence.BetaParticipationRepository;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The betas people tried and answered the question about, for the achievement „Beta-Pionier“
 * (#1501). The answers stay without the person (ADR-0039); counted is only that there was one.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Authorized
public class BetaAchievementService {

  private final BetaParticipationRepository participationRepository;
  private final AuthorizedUser authorizedUser;
  private final AuthorizedEmployee authorizedEmployee;

  /** The answered betas of every person, ordered by day, by employee id - for the nightly job. */
  @Authorized(requiresManager = true)
  public Map<Long, List<AnsweredBeta>> getAnsweredByEmployee() {
    return participationRepository.findAnswered().stream().collect(groupingBy(AnsweredBeta::employeeId));
  }

  /** How many betas one person answered - the own, or anybody's for a manager. */
  public long countAnswered(long employeeId) {
    if (!authorizedUser.isManager() && !Objects.equals(authorizedEmployee.getEmployeeId(), employeeId)) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }
    return participationRepository.countAnswered(employeeId);
  }
}
