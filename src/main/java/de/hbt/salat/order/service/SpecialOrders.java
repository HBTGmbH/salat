package de.hbt.salat.order.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.SuborderDAO;

/**
 * The orders with a role of their own (#1341, ADR-0035): the vacation order, its suborders without a
 * calculated entitlement, and the suborders of the regular training.
 *
 * <p>Which orders these are is decided in operation, not in the software: {@code application.yaml}
 * names them by sign ({@code salat.vacation}, {@code salat.training}). This class resolves the signs
 * into ids once at start and answers every question by id afterwards. A sign that names no order, or
 * an entry of {@code do-not-calculate-signs} that does not lie below the vacation order, stops the
 * start with a message naming the setting — a role that silently resolved to nothing would cost every
 * vacation account without anyone noticing. A setting left empty switches the role off.
 *
 * <p><b>The vacation order.</b> Its suborders fall into two groups: those listed in
 * {@code do-not-calculate-signs} — special leave, which has no entitlement and is summed up in one
 * line of the vacation account — and every other one, the yearly suborders. The entitlement of an
 * employee order on a yearly suborder is calculated, not entered (→
 * {@code EmployeeorderService#applyVacationEntitlement}); the year it is calculated for is the year
 * the suborder begins, not its sign.
 *
 * <p><b>The lock.</b> Since signs stay changeable (ADR-0034), a configured order could be renamed
 * away from its setting. The services refuse that instead: the sign of a configured order or
 * suborder, and of every order and suborder above a configured one, cannot be changed, a configured
 * suborder cannot be moved, and none of them can be deleted ({@link #isLockedCustomerorder},
 * {@link #isLockedSuborder}). Renaming one stays an act of operation: sign in the database and setting
 * in the same deployment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
// Which orders are special is configuration, not person data, and it is resolved at start, before
// anybody is logged in — every caller may ask.
@Authorized(permitAll = true)
public class SpecialOrders {

  private final SalatProperties properties;
  private final CustomerorderRepository customerorderRepository;
  private final SuborderDAO suborderDAO;

  private volatile Resolved resolved = Resolved.NONE;

  private record Resolved(Long vacationCustomerorderId, Set<Long> vacationDoNotCalculateSuborderIds,
                          Set<Long> regularTrainingSuborderIds, Set<Long> lockedCustomerorderIds,
                          Set<Long> lockedSuborderIds) {

    static final Resolved NONE = new Resolved(null, Set.of(), Set.of(), Set.of(), Set.of());
  }

  /**
   * Resolves the configured signs into ids. Runs once at start; a test that creates the orders after
   * the start sets the properties and calls it again.
   *
   * @throws IllegalStateException when a sign names no order, or an entry of
   *                               {@code do-not-calculate-signs} does not lie below the vacation order
   */
  @EventListener(ApplicationReadyEvent.class)
  @Transactional(readOnly = true)
  public void resolve() {
    var vacationSign = trimToNull(properties.getVacation().getCustomerorderSign());
    var doNotCalculateSigns = signs(properties.getVacation().getDoNotCalculateSigns());
    var trainingSigns = signs(properties.getTraining().getRegularSuborderSigns());

    Long vacationId = null;
    if (vacationSign != null) {
      vacationId = customerorderRepository.findBySign(vacationSign)
          .orElseThrow(() -> new IllegalStateException(
              "salat.vacation.customerorder-sign: no customer order has the sign '" + vacationSign + "'"))
          .getId();
    }

    var suborderBySign = doNotCalculateSigns.isEmpty() && trainingSigns.isEmpty()
        ? Map.<String, Suborder>of() : suborderBySign();
    var locked = new HashSet<Long>();
    var lockedOrders = new HashSet<Long>();
    if (vacationId != null) {
      lockedOrders.add(vacationId);
    }

    var doNotCalculate = new HashSet<Long>();
    for (var sign : doNotCalculateSigns) {
      var suborder = suborderNamed(suborderBySign, sign, "salat.vacation.do-not-calculate-signs");
      if (!Objects.equals(suborder.getCustomerorder().getId(), vacationId)) {
        throw new IllegalStateException("salat.vacation.do-not-calculate-signs: '" + sign
            + "' does not lie below the vacation order '" + vacationSign + "'");
      }
      doNotCalculate.add(suborder.getId());
      lock(suborder, locked, lockedOrders);
    }
    var training = new HashSet<Long>();
    for (var sign : trainingSigns) {
      var suborder = suborderNamed(suborderBySign, sign, "salat.training.regular-suborder-signs");
      training.add(suborder.getId());
      lock(suborder, locked, lockedOrders);
    }

    resolved = new Resolved(vacationId, Set.copyOf(doNotCalculate), Set.copyOf(training),
        Set.copyOf(lockedOrders), Set.copyOf(locked));
    if (vacationId != null) {
      warnAboutSuspiciousYearlySuborders(vacationId, doNotCalculate);
    }
    log.info("Special orders: vacation order {} (id {}), without calculated entitlement {}, regular training {}",
        vacationSign, vacationId, doNotCalculateSigns, trainingSigns);
  }

  /** The vacation order, {@code null} while the role is off. */
  public Long getVacationCustomerorderId() {
    return resolved.vacationCustomerorderId();
  }

  public boolean isVacationOrder(long customerorderId) {
    return Objects.equals(resolved.vacationCustomerorderId(), customerorderId);
  }

  /** A suborder of the vacation order without a calculated entitlement — special leave. */
  public boolean isVacationDoNotCalculate(long suborderId) {
    return resolved.vacationDoNotCalculateSuborderIds().contains(suborderId);
  }

  /**
   * A yearly suborder of the vacation order: one whose employee orders carry a calculated
   * entitlement — every suborder of the vacation order that is not listed as without one.
   */
  public boolean isVacationYear(long customerorderId, long suborderId) {
    return isVacationOrder(customerorderId) && !isVacationDoNotCalculate(suborderId);
  }

  /** A suborder of the regular training, as opposed to training booked on a project. */
  public boolean isRegularTraining(long suborderId) {
    return resolved.regularTrainingSuborderIds().contains(suborderId);
  }

  /** Whether the sign of the customer order must not change and the order must not be deleted. */
  public boolean isLockedCustomerorder(long customerorderId) {
    return resolved.lockedCustomerorderIds().contains(customerorderId);
  }

  /** Whether the sign of the suborder must not change, and it must neither be moved nor deleted. */
  public boolean isLockedSuborder(long suborderId) {
    return resolved.lockedSuborderIds().contains(suborderId);
  }

  /**
   * Every suborder of the vacation order outside the list counts as a yearly one and gets an
   * entitlement for the year it begins. One whose sign does not name that year is most likely
   * special leave missing from {@code do-not-calculate-signs} — worth a warning, not a stop: the sign
   * is free since #1341. A suborder that ended before the current year is left out; a relic like that
   * gets no new employee order, and warning about it on every start would only be noise.
   */
  private void warnAboutSuspiciousYearlySuborders(long vacationId, Set<Long> doNotCalculate) {
    var yearStart = DateUtils.today().withDayOfYear(1);
    for (var suborder : suborderDAO.getSubordersByCustomerorderId(vacationId)) {
      var countsAsYearly = !doNotCalculate.contains(suborder.getId());
      if (countsAsYearly && !hasEndedBefore(suborder, yearStart) && !signNamesYearOfBegin(suborder)) {
        log.warn("Suborder {} of the vacation order counts as the yearly suborder of {}; if it has no entitlement,"
            + " list it in salat.vacation.do-not-calculate-signs", suborder.getCompleteOrderSign(),
            suborder.getFromDate().getYear());
      }
    }
  }

  private static boolean hasEndedBefore(Suborder suborder, LocalDate day) {
    var until = suborder.getUntilDate();
    return until != null && until.isBefore(day);
  }

  /** Whether the sign is the year the suborder begins — how yearly suborders have always been named. */
  private static boolean signNamesYearOfBegin(Suborder suborder) {
    var from = suborder.getFromDate();
    return from == null || String.valueOf(from.getYear()).equals(suborder.getSign());
  }

  /** A configured suborder and everything above it: renaming any of them changes its complete sign. */
  private static void lock(Suborder suborder, Set<Long> lockedSuborders, Set<Long> lockedOrders) {
    lockedOrders.add(suborder.getCustomerorder().getId());
    for (var current = suborder; current != null && lockedSuborders.add(current.getId());
        current = current.getParentorder()) {
      // the walk ends at the top, or at a suborder already locked — a cycle would end there too
    }
  }

  private Map<String, Suborder> suborderBySign() {
    var bySign = new HashMap<String, Suborder>();
    suborderDAO.getSuborders().forEach(suborder -> bySign.put(suborder.getCompleteOrderSign(), suborder));
    return bySign;
  }

  private static Suborder suborderNamed(Map<String, Suborder> suborderBySign, String sign, String setting) {
    var suborder = suborderBySign.get(sign);
    if (suborder == null) {
      throw new IllegalStateException(setting + ": no suborder has the complete sign '" + sign + "'");
    }
    return suborder;
  }

  private static List<String> signs(List<String> configured) {
    return configured == null ? List.of()
        : configured.stream().map(SpecialOrders::trimToNull).filter(Objects::nonNull).toList();
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
