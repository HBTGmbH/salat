package de.hbt.salat.palette.service;

import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static java.util.stream.Collectors.groupingBy;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.SalatUserService;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.palette.domain.PaletteUse;
import de.hbt.salat.palette.domain.PaletteUseDay;
import de.hbt.salat.palette.persistence.PaletteLoginReferences;
import de.hbt.salat.palette.persistence.PaletteUseRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Counts the runs of the command palette per login and day (#1501), for the achievement „Palette
 * Junkie“ - the number only. A run made while acting as somebody else is not counted: the other
 * person did not use the palette.
 *
 * <p>Counting must never be the reason a request fails, the pattern of {@code BetaUsageService}:
 * each write in a transaction of its own, and whatever goes wrong is swallowed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Authorized
public class PaletteUseService {

  private final PaletteUseRepository repository;
  private final PaletteLoginReferences loginReferences;
  private final SalatUserService salatUserService;
  private final AuthorizedUser authorizedUser;
  private final PlatformTransactionManager transactionManager;

  public void countForCurrentUser() {
    try {
      if (authorizedUser.getImpersonateLoginSign() != null) {
        return;
      }
      currentLogin().ifPresent(salatUserId -> record(salatUserId, ClockProvider.today()));
    } catch (RuntimeException e) {
      log.debug("Could not count a run of the palette", e);
    }
  }

  /** The runs per day of every login, ordered by day, by login id - for the nightly job. */
  @Authorized(requiresManager = true)
  @Transactional(readOnly = true)
  public Map<Long, List<PaletteUseDay>> getUseDaysByLogin() {
    return repository.findAllDays().stream().collect(groupingBy(PaletteUseDay::salatUserId));
  }

  /** All runs of one login - the own, or anybody's for a manager. */
  @Transactional(readOnly = true)
  public long getUses(long salatUserId) {
    var effective = salatUserService.getSalatUserIdByLoginname(authorizedUser.getEffectiveLoginSign());
    if (!authorizedUser.isManager() && !Objects.equals(effective.orElse(null), salatUserId)) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }
    return repository.sumUses(salatUserId);
  }

  private Optional<Long> currentLogin() {
    return salatUserService.getSalatUserIdByLoginname(authorizedUser.getLoginSign());
  }

  /** Raises the day's row, or creates it; of two requests creating it at once the loser raises it. */
  private void record(long salatUserId, LocalDate day) {
    var transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    if (Boolean.TRUE.equals(transaction.execute(status -> repository.increment(salatUserId, day) > 0))) {
      return;
    }
    try {
      transaction.executeWithoutResult(status -> {
        var use = new PaletteUse();
        use.setSalatUser(loginReferences.salatUser(salatUserId));
        use.setUseDate(day);
        use.setUseCount(1);
        repository.saveAndFlush(use);
      });
    } catch (DataIntegrityViolationException e) {
      transaction.executeWithoutResult(status -> repository.increment(salatUserId, day));
    }
  }
}
