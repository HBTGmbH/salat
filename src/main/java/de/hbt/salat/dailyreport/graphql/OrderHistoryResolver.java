package de.hbt.salat.dailyreport.graphql;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.dailyreport.domain.OrderHistory;
import de.hbt.salat.dailyreport.service.OrderHistoryService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.order.domain.CustomerorderReadModel;
import de.hbt.salat.order.service.CustomerorderService;

/**
 * On which orders a person worked, and in which periods (#1534): the history from
 * {@link OrderHistoryService}, the orders from the order module as values, for all orders of a response at once.
 */
@Controller
@Authorized(requireUnrestricted = true)
@RequiredArgsConstructor
class OrderHistoryResolver {

  private final OrderHistoryService orderHistoryService;
  private final CustomerorderService customerorderService;
  private final AuthorizedEmployee authorizedEmployee;

  @QueryMapping
  @Authorized(requireUnrestricted = true)
  List<OrderHistory> orderHistory(@Argument Long employeeId, @Argument Integer gapDays,
      @Argument Boolean billableOnly) {
    return orderHistoryService.getOrderHistory(
        Objects.requireNonNullElse(employeeId, authorizedEmployee.getEmployeeId()),
        Objects.requireNonNullElse(gapDays, OrderHistoryService.DEFAULT_GAP_DAYS),
        !Boolean.FALSE.equals(billableOnly));
  }

  /**
   * The orders of the history. They are read without a rule of their own: every id comes from the history, and an
   * order is in it only where the user may read bookings on it, so these are orders the booking list names to the
   * user anyway. The fields are those the order list shows everybody who may open it.
   */
  @BatchMapping(typeName = "OrderHistoryEntry")
  @Authorized(requireUnrestricted = true)
  List<CustomerorderReadModel> customerorder(List<OrderHistory> histories) {
    Set<Long> ids = histories.stream().map(OrderHistory::customerorderId).collect(Collectors.toSet());
    Map<Long, CustomerorderReadModel> orders = customerorderService.getCustomerorderReadModelsByIds(ids);
    return histories.stream().map(history -> orders.get(history.customerorderId())).toList();
  }
}
