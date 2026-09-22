package org.tb.jira.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.tb.common.domain.AuditedEntity;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Which suborders the scope of a replication covers (#1007) — the whole order, or one suborder with
 * the branch below it.
 *
 * <p>Its own bean rather than a method of {@link JiraWorklogSyncService}, and transactional: walking
 * the order tree touches lazy associations ({@code Suborder.parentorder} and
 * {@code Suborder.suborders}), so it needs an open session. The sync itself must not run inside a
 * transaction — it talks to JIRA over HTTP and would hold a database connection for the whole of a
 * foreign system's response time — and a transactional method called on {@code this} would not be
 * one anyway.
 *
 * <p>Hidden and expired suborders count: {@code hide} declutters pickers and says nothing about
 * whether time was booked. Leaving them out would silently drop their bookings from the sums.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JiraScopeSuborders {

  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;

  /**
   * The ids of every suborder in the scope, the named one included. Empty when the scope points at
   * nothing — an order that was renamed after the config was written.
   *
   * <p>An order sign wins over a suborder path that reads the same, exactly as in
   * {@code JiraReplicationConfigService.customerorderSignOf}: every config written before #1025
   * carries an order sign and has to keep meaning "the whole order". Asked rather than parsed —
   * splitting at the first slash would read {@code 0283/03.20/F&E/01} as the order {@code 0283}.
   */
  List<Long> idsOf(String scopeSign) {
    if (scopeSign == null || scopeSign.isBlank()) {
      return List.of();
    }
    if (customerorderService.getCustomerorderBySign(scopeSign) != null) {
      return suborderService.getSubordersByCustomerorderSigns(List.of(scopeSign)).stream()
          .map(AuditedEntity::getId)
          .toList();
    }
    var suborder = suborderService.getSuborderByCompleteOrderSign(scopeSign);
    if (suborder == null) {
      log.warn("JIRA scope {} matches neither a customer order nor a suborder - no bookings are "
          + "evaluated for it", scopeSign);
      return List.of();
    }
    return suborder.getAllChildren().stream()
        .map(AuditedEntity::getId)
        .toList();
  }
}
