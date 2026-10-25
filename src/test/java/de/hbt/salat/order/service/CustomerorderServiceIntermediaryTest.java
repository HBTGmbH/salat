package de.hbt.salat.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.VetoedException;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.customer.event.CustomerDeleteEvent;
import de.hbt.salat.customer.persistence.CustomerDAO;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderDTO;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.SurchargeInput;
import de.hbt.salat.order.domain.TicketReferenceMode;
import de.hbt.salat.order.persistence.CustomerorderDAO;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.TagReferences;
import de.hbt.salat.tag.service.TagService;

/**
 * The intermediary of an order (#1536): none, or a customer marked as intermediary that is not the order's own
 * customer; a stored one stays when it lost its mark; and a customer who mediates an order cannot be deleted.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
class CustomerorderServiceIntermediaryTest {

  private static final long ORDER_ID = 7L;
  private static final long CUSTOMER_ID = 1L;
  private static final long INTERMEDIARY_ID = 2L;
  private static final long UNMARKED_ID = 3L;
  private static final LocalDate FROM = LocalDate.parse("2026-01-01");

  private CustomerorderService customerorderService;
  private CustomerorderRepository customerorderRepository;
  private CustomerorderDAO customerorderDAO;
  private Customerorder order;
  private Customer intermediary;
  private Customer unmarked;

  @BeforeEach
  void setUp() {
    customerorderDAO = mock(CustomerorderDAO.class);
    var customerDAO = mock(CustomerDAO.class);
    customerorderRepository = mock(CustomerorderRepository.class,
        invocation -> invocation.getMethod().getName().equals("save") ? invocation.getArgument(0) : null);
    customerorderService = new CustomerorderService(mock(ApplicationEventPublisher.class), mock(CommandPublisher.class),
        customerorderDAO, customerDAO, mock(EmployeeDAO.class), customerorderRepository, mock(SpecialOrders.class),
        mock(TagService.class), mock(TagReferences.class));

    var customer = customer(CUSTOMER_ID, false);
    intermediary = customer(INTERMEDIARY_ID, true);
    unmarked = customer(UNMARKED_ID, false);
    when(customerDAO.getCustomerById(CUSTOMER_ID)).thenReturn(customer);
    when(customerDAO.getCustomerById(INTERMEDIARY_ID)).thenReturn(intermediary);
    when(customerDAO.getCustomerById(UNMARKED_ID)).thenReturn(unmarked);

    order = new Customerorder();
    setField(order, "id", ORDER_ID);
    order.setSign("4711");
    order.setFromDate(FROM);
    when(customerorderDAO.getCustomerorderById(ORDER_ID)).thenReturn(order);
  }

  @Test
  void a_customer_marked_as_intermediary_is_stored() {
    customerorderService.update(ORDER_ID, dto(INTERMEDIARY_ID));

    assertThat(order.getIntermediary()).isSameAs(intermediary);
  }

  @Test
  void without_an_intermediary_the_order_has_none() {
    order.setIntermediary(intermediary);

    customerorderService.update(ORDER_ID, dto(null));

    assertThat(order.getIntermediary()).isNull();
  }

  @Test
  void the_customer_of_the_order_cannot_be_its_intermediary() {
    assertRefused(dto(CUSTOMER_ID), ErrorCode.CO_INTERMEDIARY_IS_CUSTOMER);
  }

  @Test
  void a_customer_not_marked_as_intermediary_is_refused() {
    assertRefused(dto(UNMARKED_ID), ErrorCode.CO_INTERMEDIARY_NOT_MARKED);
  }

  /** Saving an order must not clear an intermediary that lost its mark since it was chosen. */
  @Test
  void a_stored_intermediary_stays_after_losing_its_mark() {
    order.setIntermediary(unmarked);

    customerorderService.update(ORDER_ID, dto(UNMARKED_ID));

    assertThat(order.getIntermediary()).isSameAs(unmarked);
  }

  @Test
  void a_customer_who_mediates_an_order_cannot_be_deleted() {
    when(customerorderRepository.findSignsByIntermediaryId(INTERMEDIARY_ID)).thenReturn(List.of("4711", "4712"));

    assertThatThrownBy(() -> customerorderService.onCustomerDelete(new CustomerDeleteEvent(INTERMEDIARY_ID)))
        .isInstanceOfSatisfying(VetoedException.class, e -> {
          assertThat(e.getMessages().getFirst().getErrorCode()).isEqualTo(ErrorCode.CO_INTERMEDIARY_IN_USE);
          assertThat(e.getMessages().getFirst().getArguments()).containsExactly("4711, 4712");
        });
    verify(customerorderRepository, never()).findAllByCustomerId(INTERMEDIARY_ID);
  }

  @Test
  void a_customer_who_mediates_nothing_takes_its_own_orders_along_as_before() {
    when(customerorderRepository.findSignsByIntermediaryId(CUSTOMER_ID)).thenReturn(List.of());
    when(customerorderRepository.findAllByCustomerId(CUSTOMER_ID)).thenReturn(List.of());

    customerorderService.onCustomerDelete(new CustomerDeleteEvent(CUSTOMER_ID));

    verify(customerorderRepository).findAllByCustomerId(CUSTOMER_ID);
  }

  private void assertRefused(CustomerorderDTO dto, ErrorCode errorCode) {
    assertThatThrownBy(() -> customerorderService.update(ORDER_ID, dto))
        .isInstanceOfSatisfying(ErrorCodeException.class,
            e -> assertThat(e.getMessages().getFirst().getErrorCode()).isEqualTo(errorCode));
  }

  private static Customer customer(long id, boolean intermediary) {
    var customer = new Customer();
    setField(customer, "id", id);
    customer.setShortname("K" + id);
    customer.setIntermediary(intermediary);
    return customer;
  }

  private static CustomerorderDTO dto(Long intermediaryId) {
    return new CustomerorderDTO(CUSTOMER_ID, FROM, null, "4711", "Auftrag", "Auftrag", null, null, null, List.of(1L),
        1L, null, null, false, OrderType.STANDARD, TicketReferenceMode.UNLIMITED, null, SurchargeInput.NONE, null,
        intermediaryId);
  }
}
