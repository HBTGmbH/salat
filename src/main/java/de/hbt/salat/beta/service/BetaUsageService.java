package de.hbt.salat.beta.service;

import static de.hbt.salat.common.util.ClockProvider.today;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import java.time.LocalDate;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.beta.domain.BetaUsage;
import de.hbt.salat.beta.domain.BetaVariant;
import de.hbt.salat.beta.persistence.BetaEmployeeReferences;
import de.hbt.salat.beta.persistence.BetaUsageRepository;
import de.hbt.salat.common.beta.BetaFeature;

/**
 * Counts the uses of a beta (#1447) — on both sides of it: with the beta switched on as
 * {@link BetaVariant#BETA}, otherwise as {@link BetaVariant#CLASSIC}, the comparison group. The
 * events are the caller's: this service makes no assumption about them and counts any well-formed
 * key ({@link #isEventKey}) for a beta that exists, and only for the logged-in person
 * acting as themselves ({@link MeasuredPerson}).
 *
 * <p>Counting must never be the reason a request fails. It therefore runs outside the caller's
 * transaction, each write in a transaction of its own, and swallows whatever goes wrong: a lost count
 * costs less than a lost booking. Not {@code @Transactional} on purpose — a failure inside a joined
 * transaction would mark the caller's for rollback. Count from the controller, after the action
 * itself has been done: called inside a service's transaction, the read of the switch would still
 * join it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Authorized
public class BetaUsageService {

  private final BetaFeatureRegistry registry;
  private final BetaFeatureService betaFeatureService;
  private final BetaUsageRepository usageRepository;
  private final BetaEmployeeReferences employeeReferences;
  private final MeasuredPerson measuredPerson;
  private final PlatformTransactionManager transactionManager;

  /** Lower case letters, digits, dot and dash, as {@code favorite-applied}; at most 64 characters. */
  private static final Pattern EVENT_KEY = Pattern.compile("[a-z0-9][a-z0-9.-]{0,63}");

  public void count(BetaFeature feature, String event) {
    count(feature.getKey(), event);
  }

  /** The same by key, for an event that happens in the browser alone. */
  public void count(String featureKey, String event) {
    try {
      var countable = registry.isKnown(featureKey) && isEventKey(event);
      var employeeId = measuredPerson.employeeId();
      if (!countable || employeeId.isEmpty()) {
        return;
      }
      var variant = betaFeatureService.isEnabledForCurrentUser(featureKey) ? BetaVariant.BETA : BetaVariant.CLASSIC;
      record(featureKey, event, employeeId.get(), today(), variant);
    } catch (RuntimeException e) {
      log.debug("Could not count use {} of beta {}", event, featureKey, e);
    }
  }

  /**
   * Whether {@code event} can be counted as an event key. It guards the column, not the meaning: an
   * event belongs to the module that counts it, and a key from the browser is taken as it comes.
   */
  static boolean isEventKey(String event) {
    return event != null && EVENT_KEY.matcher(event).matches();
  }

  /**
   * Raises the day's row, or creates it. Two requests creating the same row at once collide on its
   * unique key; the loser raises the row the winner created.
   */
  private void record(String featureKey, String event, long employeeId, LocalDate date, BetaVariant variant) {
    var transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    Boolean raised = transaction.execute(status ->
        usageRepository.increment(featureKey, event, employeeId, date, variant) > 0);
    if (Boolean.TRUE.equals(raised)) {
      return;
    }
    try {
      transaction.executeWithoutResult(status -> {
        var usage = new BetaUsage();
        usage.setFeatureKey(featureKey);
        usage.setEventKey(event);
        usage.setEmployee(employeeReferences.employee(employeeId));
        usage.setUsageDate(date);
        usage.setVariant(variant);
        usage.setUseCount(1);
        usageRepository.saveAndFlush(usage);
      });
    } catch (DataIntegrityViolationException e) {
      transaction.executeWithoutResult(status -> usageRepository.increment(featureKey, event, employeeId, date, variant));
    }
  }
}
