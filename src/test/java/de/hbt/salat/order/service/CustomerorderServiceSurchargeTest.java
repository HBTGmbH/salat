package de.hbt.salat.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static de.hbt.salat.order.domain.SurchargeKind.HOLIDAY;
import static de.hbt.salat.order.domain.SurchargeKind.NIGHT;
import static de.hbt.salat.order.domain.SurchargeKind.WEEKEND;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.customer.persistence.CustomerDAO;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderDTO;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.SurchargeInput;
import de.hbt.salat.order.domain.SurchargeRate;
import de.hbt.salat.order.domain.TicketReferenceMode;
import de.hbt.salat.order.persistence.CustomerorderDAO;
import de.hbt.salat.order.persistence.CustomerorderRepository;

/**
 * The time surcharge rates of an order as the form sends them (#1469): an empty percent means no
 * surcharge of that kind, a night rate needs its night, and the rates are checked before anything is
 * stored.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
class CustomerorderServiceSurchargeTest {

  private static final long ORDER_ID = 7L;
  private static final LocalDate FROM = LocalDate.parse("2026-01-01");
  private static final LocalTime TWENTY = LocalTime.of(20, 0);
  private static final LocalTime SIX = LocalTime.of(6, 0);

  private CustomerorderService customerorderService;
  private Customerorder order;

  @BeforeEach
  void setUp() {
    var customerorderDAO = mock(CustomerorderDAO.class);
    customerorderService = new CustomerorderService(mock(ApplicationEventPublisher.class), mock(CommandPublisher.class),
        customerorderDAO, mock(CustomerDAO.class), mock(EmployeeDAO.class), mock(CustomerorderRepository.class,
            invocation -> invocation.getMethod().getName().equals("save") ? invocation.getArgument(0) : null),
        mock(SpecialOrders.class));

    order = new Customerorder();
    setField(order, "id", ORDER_ID);
    order.setSign("4711");
    order.setFromDate(FROM);
    when(customerorderDAO.getCustomerorderById(ORDER_ID)).thenReturn(order);
  }

  @Test
  void the_rates_are_stored_and_an_empty_percent_means_none() {
    customerorderService.update(ORDER_ID, dto(new SurchargeInput(25, TWENTY, SIX, 50, null)));

    assertThat(order.getSurchargeRate(NIGHT)).isEqualTo(new SurchargeRate(25, TWENTY, SIX));
    assertThat(order.getSurchargeRate(WEEKEND)).isEqualTo(SurchargeRate.of(50));
    assertThat(order.getSurchargeRate(HOLIDAY)).isNull();
  }

  /** The form fills the night times in advance; without a night percent they are not stored. */
  @Test
  void night_times_without_a_night_percent_are_dropped() {
    customerorderService.update(ORDER_ID, dto(new SurchargeInput(null, TWENTY, SIX, null, null)));

    assertThat(order.getSurchargeRate(NIGHT)).isNull();
  }

  @Test
  void a_rate_out_of_range_is_refused() {
    assertRefused(new SurchargeInput(null, null, null, null, 1001), ErrorCode.CO_SURCHARGE_PERCENT_INVALID);
  }

  @Test
  void a_night_rate_without_its_night_is_refused() {
    assertRefused(new SurchargeInput(25, TWENTY, null, null, null), ErrorCode.CO_SURCHARGE_NIGHT_TIMES_MISSING);
    assertThat(order.getSurchargeRate(NIGHT)).isNull();
  }

  private void assertRefused(SurchargeInput input, ErrorCode errorCode) {
    assertThatThrownBy(() -> customerorderService.update(ORDER_ID, dto(input)))
        .isInstanceOfSatisfying(ErrorCodeException.class,
            e -> assertThat(e.getMessages().getFirst().getErrorCode()).isEqualTo(errorCode));
  }

  private static CustomerorderDTO dto(SurchargeInput surcharges) {
    return new CustomerorderDTO(1L, FROM, null, "4711", "Auftrag", "Auftrag", null, null, null, List.of(1L), 1L,
        null, null, false, OrderType.STANDARD, TicketReferenceMode.UNLIMITED, null, surcharges);
  }
}
