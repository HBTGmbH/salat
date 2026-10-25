package de.hbt.salat.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.customer.persistence.CustomerDAO;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.persistence.CustomerorderDAO;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.TagReferences;
import de.hbt.salat.tag.service.TagService;

/**
 * Whether an order of the read model is a customer order (#1539): asked once for all orders of a call, never per order.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CustomerorderServiceReadModelTest {

  private final CustomerorderRepository customerorderRepository = mock(CustomerorderRepository.class);
  private final CustomerorderService customerorderService = new CustomerorderService(
      mock(ApplicationEventPublisher.class), mock(CommandPublisher.class), mock(CustomerorderDAO.class),
      mock(CustomerDAO.class), mock(EmployeeDAO.class), customerorderRepository, mock(SpecialOrders.class),
      mock(TagService.class), mock(TagReferences.class));

  @Test
  void the_read_models_say_which_orders_have_a_billable_suborder_from_one_query_for_all() {
    var ids = List.of(1L, 2L, 3L);
    when(customerorderRepository.findWithCustomerByIdIn(ids)).thenReturn(List.of(order(1L), order(2L), order(3L)));
    when(customerorderRepository.findInvoiceableIdsByIdIn(ids)).thenReturn(Set.of(1L, 3L));

    var readModels = customerorderService.getCustomerorderReadModelsByIds(ids);

    assertThat(readModels.get(1L).billable()).isTrue();
    assertThat(readModels.get(2L).billable()).isFalse();
    assertThat(readModels.get(3L).billable()).isTrue();
    verify(customerorderRepository, times(1)).findInvoiceableIdsByIdIn(anyCollection());
  }

  @Test
  void no_ids_ask_nothing() {
    assertThat(customerorderService.getCustomerorderReadModelsByIds(List.of())).isEmpty();
    verify(customerorderRepository, times(0)).findInvoiceableIdsByIdIn(anyCollection());
  }

  private static Customerorder order(long id) {
    var customer = new Customer();
    setField(customer, "id", 100 + id);
    customer.setShortname("C" + id);
    var order = new Customerorder();
    setField(order, "id", id);
    order.setSign("ABC-" + id);
    order.setCustomer(customer);
    order.setFromDate(LocalDate.of(2026, 1, 1));
    order.setDebithours(Duration.ZERO);
    order.setHide(false);
    return order;
  }
}
