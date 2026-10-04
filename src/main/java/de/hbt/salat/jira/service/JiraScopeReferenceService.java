package de.hbt.salat.jira.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraTicketRepository;
import de.hbt.salat.jira.persistence.JiraWorklogSyncRepository;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * What the JIRA module does when the order tree changes (#1322, #1323): it keeps the scope sign of
 * its replications, tickets and worklogs in step, says how many replications still refer to an order
 * or a suborder that is about to go, and takes the tickets and worklogs of a scope along when it
 * goes — the counterpart of {@code OrderReferenceService} in the budget module.
 *
 * <p>Replications, tickets and worklogs refer to order and suborder by id, and the application
 * resolves by that id alone. The sign columns stay next to the ids only because reports and ETL
 * definitions still read them; a stale sign there would let them lose the tickets of a renamed
 * order.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class JiraScopeReferenceService {

  private final JiraReplicationConfigRepository configRepository;
  private final JiraTicketRepository ticketRepository;
  private final JiraWorklogSyncRepository worklogSyncRepository;
  private final JiraScopes scopes;

  /**
   * Rewrites the scope sign of every replication, ticket and worklog of the order from the current
   * tree.
   *
   * <p>Called while the order is being updated, before it is saved and inside the same transaction.
   * The suborders read here are the managed instances of that transaction, so a complete order sign
   * is already the new one. Tickets and worklogs are rewritten in bulk, and only where the sign
   * differs — an order carries thousands of tickets, and a save that renames nothing writes nothing.
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
    ticketRepository.mirrorOrderWide(customerorder.getId(), customerorder.getSign());
    worklogSyncRepository.mirrorOrderWide(customerorder.getId(), customerorder.getSign());
    var suborderIds = new LinkedHashSet<Long>(ticketRepository.findSuborderIdsOfCustomerorder(customerorder.getId()));
    suborderIds.addAll(worklogSyncRepository.findSuborderIdsOfCustomerorder(customerorder.getId()));
    for (var suborderId : suborderIds) {
      var sign = scopes.signOf(customerorder.getId(), suborderId);
      if (sign != null) {
        mirrorSuborder(suborderId, customerorder.getId(), sign);
      }
    }
  }

  /**
   * Rewrites order and scope sign of every replication, ticket and worklog on the suborder or below
   * it. A renamed suborder changes the complete order sign of its whole branch, and a suborder moved
   * to another customer order takes its branch along — what was replicated on it follows, since it
   * means that place in the tree and not the order it used to hang under.
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
    var suborderIds = new LinkedHashSet<Long>(ticketRepository.findSuborderIdsIn(branch.keySet()));
    suborderIds.addAll(worklogSyncRepository.findSuborderIdsIn(branch.keySet()));
    for (var suborderId : suborderIds) {
      var scope = branch.get(suborderId);
      mirrorSuborder(suborderId, scope.getCustomerorder().getId(), scope.getCompleteOrderSign());
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

  /**
   * Deletes the tickets and worklog rows of the customer order as it goes (#1323). They refer to it
   * with a foreign key, and they mean nothing without it: no replication can be configured on an
   * order that no longer exists, so they could never be found again. The worklogs SALAT wrote stay
   * in JIRA.
   *
   * <p>Called while the deletion is still open to a veto, inside its transaction: a veto from
   * elsewhere rolls this back with it.
   */
  @Authorized(requiresManager = true)
  public void deleteScopeDataOfCustomerorder(long customerorderId) {
    var tickets = ticketRepository.deleteByCustomerorderId(customerorderId);
    var worklogs = worklogSyncRepository.deleteByCustomerorderId(customerorderId);
    log.info("Customer order {} is deleted: removed {} JIRA tickets and {} worklog rows of its scopes",
        customerorderId, tickets, worklogs);
  }

  /** Same for a suborder and the branch below it, which goes with it. */
  @Authorized(requiresManager = true)
  public void deleteScopeDataOfSuborder(long suborderId) {
    Collection<Long> branch = scopes.branchOf(suborderId);
    if (branch.isEmpty()) {
      return;
    }
    var tickets = ticketRepository.deleteBySuborderIdIn(branch);
    var worklogs = worklogSyncRepository.deleteBySuborderIdIn(branch);
    log.info("Suborder {} is deleted: removed {} JIRA tickets and {} worklog rows of its branch",
        suborderId, tickets, worklogs);
  }

  private void mirrorSuborder(long suborderId, long customerorderId, String sign) {
    ticketRepository.mirrorSuborder(suborderId, customerorderId, sign);
    worklogSyncRepository.mirrorSuborder(suborderId, customerorderId, sign);
  }

}
