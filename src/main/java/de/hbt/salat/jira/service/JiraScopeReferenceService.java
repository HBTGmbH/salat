package de.hbt.salat.jira.service;

import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * What the JIRA module does when the order tree changes (#1322): it keeps the scope sign of its
 * replications in step, and it says how many still refer to an order or a suborder that is about to
 * go — the counterpart of {@code OrderReferenceService} in the budget module.
 *
 * <p>A replication refers to its order and suborder by id, and the application resolves by that id
 * alone. The sign column stays next to the ids only because reports and ETL definitions still read
 * it; a stale sign there would let them lose the replication of a renamed order.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class JiraScopeReferenceService {

  private final JiraReplicationConfigRepository configRepository;
  private final JiraScopes scopes;

  /**
   * Rewrites the scope sign of every replication of the order from the current tree.
   *
   * <p>Called while the order is being updated, before it is saved and inside the same transaction.
   * The suborders read here are the managed instances of that transaction, so a complete order sign
   * is already the new one.
   */
  @Authorized(requiresManager = true)
  public void followCustomerorder(Customerorder customerorder) {
    for (var config : configRepository.findByCustomerorderId(customerorder.getId())) {
      var sign = config.getSuborderId() == null
          ? customerorder.getSign()
          : scopes.signOf(customerorder.getId(), config.getSuborderId());
      if (sign != null) {
        config.setScopeSign(sign);
      }
    }
  }

  /**
   * Rewrites order and scope sign of every replication on the suborder or below it. A renamed
   * suborder changes the complete order sign of its whole branch, and a suborder moved to another
   * customer order takes its branch along — the replications on it follow, since they mean that
   * place in the tree and not the order it used to hang under.
   *
   * <p>Called while the suborder is being updated, before it is saved: the entity already carries
   * its new sign, parent and order.
   */
  @Authorized(requiresManager = true)
  public void followSuborder(Suborder suborder) {
    var branch = suborder.getAllChildren().stream()
        .collect(Collectors.toMap(Suborder::getId, Function.identity()));
    for (var config : configRepository.findBySuborderIdIn(branch.keySet())) {
      var scope = branch.get(config.getSuborderId());
      config.setCustomerorderId(scope.getCustomerorder().getId());
      config.setScopeSign(scope.getCompleteOrderSign());
    }
  }

  /** How many replications apply to the customer order, order-wide or on one of its suborders. */
  @Transactional(readOnly = true)
  public long countReplicationsOfCustomerorder(long customerorderId) {
    return configRepository.countByCustomerorderId(customerorderId);
  }

  /**
   * How many replications are narrowed to the suborder or to a suborder below it — deleting a
   * suborder takes its branch along.
   */
  @Transactional(readOnly = true)
  public long countReplicationsOfSuborder(long suborderId) {
    var branch = scopes.branchOf(suborderId);
    return branch.isEmpty() ? 0 : configRepository.countBySuborderIdIn(branch);
  }

}
