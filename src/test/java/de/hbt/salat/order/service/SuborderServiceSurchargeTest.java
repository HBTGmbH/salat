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
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SuborderDTO;
import de.hbt.salat.order.domain.SurchargeInput;
import de.hbt.salat.order.domain.SurchargeRate;
import de.hbt.salat.order.persistence.SuborderDAO;
import de.hbt.salat.order.persistence.SuborderRepository;

/**
 * The time surcharge rates of a suborder as the form sends them (#1469): an empty percent inherits,
 * zero withdraws the surcharge, a night rate needs its night, and the form learns what it would inherit.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
class SuborderServiceSurchargeTest {

  private static final long ORDER_ID = 7L;
  private static final long SUBORDER_ID = 9L;
  private static final long PARENT_ID = 10L;
  private static final LocalDate FROM = LocalDate.parse("2026-01-01");
  private static final LocalTime TWENTY = LocalTime.of(20, 0);
  private static final LocalTime SIX = LocalTime.of(6, 0);

  private SuborderService suborderService;
  private Customerorder order;
  private Suborder parent;
  private Suborder suborder;

  @BeforeEach
  void setUp() {
    var suborderDAO = mock(SuborderDAO.class);
    var customerorderService = mock(CustomerorderService.class);
    suborderService = new SuborderService(mock(ApplicationEventPublisher.class), mock(CommandPublisher.class),
        suborderDAO, mock(SuborderRepository.class), customerorderService, mock(SpecialOrders.class));

    order = new Customerorder();
    setField(order, "id", ORDER_ID);
    order.setSign("4711");
    order.setFromDate(FROM);
    order.setSurchargeRate(NIGHT, new SurchargeRate(25, TWENTY, SIX));
    order.setSurchargeRate(WEEKEND, SurchargeRate.of(50));
    parent = suborder(PARENT_ID, "01", null);
    parent.setSurchargeRate(WEEKEND, SurchargeRate.of(75));
    suborder = suborder(SUBORDER_ID, "02", null);

    when(customerorderService.getCustomerorderById(ORDER_ID)).thenReturn(order);
    when(suborderDAO.getSuborderById(SUBORDER_ID)).thenReturn(suborder);
    when(suborderDAO.getSuborderById(PARENT_ID)).thenReturn(parent);
    when(suborderDAO.getSubordersByCustomerorderId(ORDER_ID)).thenReturn(List.of(parent, suborder));
  }

  @Test
  void own_rates_are_stored_and_an_empty_percent_inherits() {
    suborderService.update(SUBORDER_ID, dto(new SurchargeInput(30, LocalTime.of(22, 0), SIX, null, 0)), ORDER_ID);

    assertThat(suborder.getSurchargeRate(NIGHT)).isEqualTo(new SurchargeRate(30, LocalTime.of(22, 0), SIX));
    assertThat(suborder.getSurchargeRate(WEEKEND)).isNull();
    assertThat(suborder.getEffectiveSurchargeRate(WEEKEND)).isEqualTo(SurchargeRate.of(50));
    assertThat(suborder.getSurchargeRate(HOLIDAY)).isEqualTo(SurchargeRate.of(0));
  }

  @Test
  void emptying_the_fields_inherits_again() {
    suborder.setSurchargeRate(NIGHT, SurchargeRate.of(0));

    suborderService.update(SUBORDER_ID, dto(SurchargeInput.NONE), ORDER_ID);

    assertThat(suborder.getSurchargeRate(NIGHT)).isNull();
    assertThat(suborder.getEffectiveSurchargeRate(NIGHT)).isEqualTo(new SurchargeRate(25, TWENTY, SIX));
  }

  @Test
  void a_rate_out_of_range_is_refused() {
    assertRefused(new SurchargeInput(null, null, null, 1001, null), ErrorCode.SO_SURCHARGE_PERCENT_INVALID);
    assertRefused(new SurchargeInput(-5, TWENTY, SIX, null, null), ErrorCode.SO_SURCHARGE_PERCENT_INVALID);
  }

  @Test
  void a_night_rate_without_its_night_is_refused() {
    assertRefused(new SurchargeInput(25, null, SIX, null, null), ErrorCode.SO_SURCHARGE_NIGHT_TIMES_MISSING);
  }

  /** What the form shows under each field, from the parent chosen in the form. */
  @Test
  void the_form_learns_per_kind_what_it_would_inherit_and_from_where() {
    var inherited = suborderService.getInheritedSurchargeRates(ORDER_ID, PARENT_ID);

    assertThat(inherited.get(NIGHT).rate()).isEqualTo(new SurchargeRate(25, TWENTY, SIX));
    assertThat(inherited.get(NIGHT).fromOrder()).isTrue();
    assertThat(inherited.get(WEEKEND).rate()).isEqualTo(SurchargeRate.of(75));
    assertThat(inherited.get(WEEKEND).suborder()).isSameAs(parent);
    assertThat(inherited.get(HOLIDAY).rate()).isNull();
  }

  private void assertRefused(SurchargeInput input, ErrorCode errorCode) {
    assertThatThrownBy(() -> suborderService.update(SUBORDER_ID, dto(input), ORDER_ID))
        .isInstanceOfSatisfying(ErrorCodeException.class,
            e -> assertThat(e.getMessages().getFirst().getErrorCode()).isEqualTo(errorCode));
  }

  private static SuborderDTO dto(SurchargeInput surcharges) {
    return new SuborderDTO(ORDER_ID, "02", "Leistung", "Leistung", null, 'y', false, false, false,
        OrderType.STANDARD, "2026-01-01", "", null, null, false, null, null, null, surcharges);
  }

  private Suborder suborder(long id, String sign, Suborder parentSuborder) {
    var newSuborder = new Suborder();
    setField(newSuborder, "id", id);
    newSuborder.setCustomerorder(order);
    newSuborder.setSign(sign);
    newSuborder.setParentorder(parentSuborder);
    newSuborder.setFromDate(FROM);
    return newSuborder;
  }
}
