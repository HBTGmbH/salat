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
import de.hbt.salat.dailyreport.domain.OrderHistoryPeriod;
import de.hbt.salat.dailyreport.service.OrderHistoryService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.order.domain.CustomerorderReadModel;
import de.hbt.salat.order.service.CustomerorderService;

/**
 * In which periods a person worked on which order (#1534): the periods from {@link OrderHistoryService}, the orders
 * from the order module as values, for all periods of a response at once.
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
  List<OrderHistoryPeriod> orderHistory(@Argument Long employeeId) {
    return orderHistoryService.getOrderHistory(
        Objects.requireNonNullElse(employeeId, authorizedEmployee.getEmployeeId()));
  }

  /**
   * The orders of the periods. They are read without a rule of their own: every id comes from a period, and a period
   * exists only where the user may read bookings on that order, so these are orders the booking list names to the
   * user anyway. The fields are those the order list shows everybody who may open it.
   */
  @BatchMapping(typeName = "OrderHistoryEntry")
  @Authorized(requireUnrestricted = true)
  List<CustomerorderReadModel> customerorder(List<OrderHistoryPeriod> periods) {
    Set<Long> ids = periods.stream().map(OrderHistoryPeriod::customerorderId).collect(Collectors.toSet());
    Map<Long, CustomerorderReadModel> orders = customerorderService.getCustomerorderReadModelsByIds(ids);
    return periods.stream().map(period -> orders.get(period.customerorderId())).toList();
  }
}
