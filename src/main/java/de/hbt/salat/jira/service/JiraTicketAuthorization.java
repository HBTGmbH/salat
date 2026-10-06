package de.hbt.salat.jira.service;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.service.CustomerorderService;

/**
 * Who may see and maintain the tickets of a customer order on the ticket page (#1386), after the
 * pattern of {@code BudgetAuthorization}: managers every order, everyone else the orders they are
 * responsible for ({@code responsibleHbt}) and that are not hidden — a hidden order grants nothing.
 * Restricted users get nothing.
 *
 * <p>Only the page. The tickets as suggestions and in the filter of the booking list stay where they
 * were, open to whoever books on the order.
 *
 * <p>Request scoped because the responsible orders are looked up once and asked for repeatedly — by
 * the menu on every page, and by every row of the list.
 */
@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class JiraTicketAuthorization {

  private final AuthorizedUser authorizedUser;
  private final CustomerorderService customerorderService;

  private List<CustomerorderOption> responsibleOrders;

  /** Whether the ticket page is open at all — drives the menu entry. */
  public boolean isTicketPageAvailable() {
    if (authorizedUser.isRestricted()) return false;
    return authorizedUser.isManager() || !responsibleCustomerorders().isEmpty();
  }

  public void checkTicketPageAvailable() {
    if (!isTicketPageAvailable()) {
      throw new AuthorizationException(ErrorCode.JI_TICKET_ORDER_NOT_AUTHORIZED);
    }
  }

  public boolean mayMaintain(long customerorderId) {
    if (authorizedUser.isRestricted()) return false;
    if (authorizedUser.isManager()) return true;
    return responsibleCustomerorders().stream().anyMatch(order -> order.id() == customerorderId);
  }

  /**
   * The orders whose tickets the page lists when no order is chosen (#1386): empty for a manager,
   * who sees every order, otherwise the ids of the user's own orders — none at all for a restricted
   * user.
   */
  public Optional<List<Long>> maintainableCustomerorderIds() {
    if (authorizedUser.isRestricted()) return Optional.of(List.of());
    if (authorizedUser.isManager()) return Optional.empty();
    return Optional.of(responsibleCustomerorders().stream().map(CustomerorderOption::id).toList());
  }

  public void checkMayMaintain(long customerorderId) {
    if (!mayMaintain(customerorderId)) {
      throw new AuthorizationException(ErrorCode.JI_TICKET_ORDER_NOT_AUTHORIZED);
    }
  }

  /**
   * The orders the page offers: for a manager every order not hidden plus {@code selectedId}, for a
   * responsible their own orders.
   */
  public List<CustomerorderOption> selectableCustomerorders(Long selectedId) {
    if (authorizedUser.isRestricted()) return List.of();
    if (authorizedUser.isManager()) return customerorderService.getSelectableCustomerorderOptions(selectedId);
    return responsibleCustomerorders();
  }

  private List<CustomerorderOption> responsibleCustomerorders() {
    if (responsibleOrders == null) {
      var userId = authorizedUser.getEffectiveUserId();
      responsibleOrders = userId == null ? List.of() : customerorderService.getResponsibleCustomerorderOptions(userId);
    }
    return responsibleOrders;
  }
}
