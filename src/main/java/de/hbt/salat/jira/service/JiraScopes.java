package de.hbt.salat.jira.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.order.domain.SuborderLocation;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Reads the scope of a replication from the order tree, by the ids it stores (#1322) — the whole
 * customer order, or one suborder with the branch below it (#1025).
 *
 * <p>Everything here asks the order module for plain values — ids, signs, {@link SuborderLocation}
 * — and never for an entity (→ ADR-0021). A renamed order or a moved suborder therefore changes the
 * answer, never what the scope covers.
 *
 * <p>Hidden and expired suborders count: {@code hide} declutters pickers and says nothing about
 * whether time was booked. Leaving them out would silently drop their bookings from the sums.
 */
@Component
@RequiredArgsConstructor
class JiraScopes {

  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;

  /**
   * The ids of every suborder in the scope, the named one included (#1007). Empty when the suborder
   * no longer exists.
   *
   * @param suborderId {@code null} for the whole order
   */
  List<Long> suborderIdsOf(long customerorderId, Long suborderId) {
    return suborderId == null
        ? suborderService.getSuborderIdsByCustomerorderId(customerorderId)
        : branchOf(suborderId);
  }

  /** The ids of the suborder and of every suborder below it; empty when it no longer exists. */
  List<Long> branchOf(long suborderId) {
    return suborderService.getSubtreeIds(suborderId);
  }

  /** Where the suborder sits in the order tree; empty when there is no such suborder. */
  Optional<SuborderLocation> locationOf(long suborderId) {
    return Optional.ofNullable(suborderService.getSuborderLocationsByIds(List.of(suborderId)).get(suborderId));
  }

  /** Whether the customer order exists — hidden and expired ones included. */
  boolean customerorderExists(long customerorderId) {
    return customerorderService.getCustomerorderSignsByIds(List.of(customerorderId)).containsKey(customerorderId);
  }

  /**
   * The scope as a sign, as the order tree carries it now: the order sign for the whole order, the
   * complete order sign of the suborder otherwise. {@code null} when order or suborder are gone.
   */
  String signOf(long customerorderId, Long suborderId) {
    if (suborderId == null) {
      return customerorderService.getCustomerorderSignsByIds(List.of(customerorderId)).get(customerorderId);
    }
    return suborderService.getCompleteOrderSignsByIds(List.of(suborderId)).get(suborderId);
  }

  /** {@link #signOf} for several configs at once, by config id — one query for orders and one for suborders. */
  Map<Long, String> signsOf(Collection<JiraReplicationConfig> configs) {
    var orderSigns = customerorderService.getCustomerorderSignsByIds(
        configs.stream().map(JiraReplicationConfig::getCustomerorderId).collect(Collectors.toSet()));
    var suborderSigns = suborderService.getCompleteOrderSignsByIds(
        configs.stream().map(JiraReplicationConfig::getSuborderId).filter(Objects::nonNull)
            .collect(Collectors.toSet()));
    return configs.stream()
        .filter(config -> signFrom(config, orderSigns, suborderSigns) != null)
        .collect(Collectors.toMap(JiraReplicationConfig::getId,
            config -> signFrom(config, orderSigns, suborderSigns), (one, other) -> one));
  }

  /**
   * {@link #signOf} for several scopes at once (#1386) — one query for orders and one for suborders.
   * A scope is the pair of order id and suborder id, the latter {@code null} for the whole order.
   */
  Map<List<Long>, String> signsOfScopes(Collection<List<Long>> scopePairs) {
    var orderSigns = customerorderService.getCustomerorderSignsByIds(
        scopePairs.stream().map(List::getFirst).collect(Collectors.toSet()));
    var suborderSigns = suborderService.getCompleteOrderSignsByIds(
        scopePairs.stream().map(List::getLast).filter(Objects::nonNull).collect(Collectors.toSet()));
    var signs = new HashMap<List<Long>, String>();
    for (var scope : scopePairs) {
      var sign = scope.getLast() == null ? orderSigns.get(scope.getFirst()) : suborderSigns.get(scope.getLast());
      if (sign != null) signs.put(scope, sign);
    }
    return signs;
  }

  private static String signFrom(JiraReplicationConfig config, Map<Long, String> orderSigns,
                                 Map<Long, String> suborderSigns) {
    return config.getSuborderId() == null
        ? orderSigns.get(config.getCustomerorderId())
        : suborderSigns.get(config.getSuborderId());
  }
}
