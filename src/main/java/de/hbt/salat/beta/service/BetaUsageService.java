package de.hbt.salat.beta.service;

import static de.hbt.salat.common.util.ClockProvider.today;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.beta.domain.BetaFeature;
import de.hbt.salat.beta.domain.BetaUsage;
import de.hbt.salat.beta.domain.BetaVariant;
import de.hbt.salat.beta.persistence.BetaEmployeeReferences;
import de.hbt.salat.beta.persistence.BetaUsageRepository;

/**
 * Counts the uses of a beta (#1447) — on both sides of it: with the beta switched on as
 * {@link BetaVariant#BETA}, otherwise as {@link BetaVariant#CLASSIC}, the comparison group. Only the
 * events a beta declares are counted, and only for the logged-in person acting as themselves
 * ({@link MeasuredPerson}).
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

  private final BetaCatalog catalog;
  private final BetaFeatureService betaFeatureService;
  private final BetaUsageRepository usageRepository;
  private final BetaEmployeeReferences employeeReferences;
  private final MeasuredPerson measuredPerson;
  private final PlatformTransactionManager transactionManager;

  public void count(BetaFeature feature, String event) {
    count(feature.getKey(), event);
  }

  /** The same by key, for an event that happens in the browser alone. */
  public void count(String featureKey, String event) {
    try {
      var declared = catalog.find(featureKey).filter(definition -> definition.declares(event)).isPresent();
      var employeeId = measuredPerson.employeeId();
      if (!declared || employeeId.isEmpty()) {
        return;
      }
      var variant = betaFeatureService.isEnabledForCurrentUser(featureKey) ? BetaVariant.BETA : BetaVariant.CLASSIC;
      record(featureKey, event, employeeId.get(), today(), variant);
    } catch (RuntimeException e) {
      log.debug("Could not count use {} of beta {}", event, featureKey, e);
    }
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
