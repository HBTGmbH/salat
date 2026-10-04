package de.hbt.salat.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.SuborderDAO;

/** The orders with a role of their own are configuration, resolved once into ids (#1341). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SpecialOrdersTest {

  private final SalatProperties properties = new SalatProperties();
  private final CustomerorderRepository customerorderRepository = mock(CustomerorderRepository.class);
  private final SuborderDAO suborderDAO = mock(SuborderDAO.class);
  private final SpecialOrders specialOrders = new SpecialOrders(properties, customerorderRepository, suborderDAO);

  private Customerorder vacation;
  private Suborder year;
  private Suborder specialLeave;
  private Customerorder training;
  private Suborder trainingBranch;
  private Suborder regularTraining;
  private Suborder otherBranch;

  @BeforeEach
  void setUp() {
    vacation = customerorder(1L, "URLAUB");
    year = suborder(10L, vacation, null, "2026");
    specialLeave = suborder(11L, vacation, null, "Sonderurlaub");
    training = customerorder(2L, "i976");
    trainingBranch = suborder(20L, training, null, "A");
    regularTraining = suborder(21L, training, trainingBranch, "FORTBILDUNG");
    otherBranch = suborder(22L, training, null, "B");
    when(customerorderRepository.findBySign("URLAUB")).thenReturn(Optional.of(vacation));
    when(customerorderRepository.findBySign("i976")).thenReturn(Optional.of(training));
    when(suborderDAO.getSuborders()).thenReturn(List.of(year, specialLeave, trainingBranch, regularTraining, otherBranch));
    when(suborderDAO.getSubordersByCustomerorderId(anyLong())).thenReturn(List.of(year, specialLeave));
  }

  @Test
  void resolves_the_configured_signs_into_ids() {
    configure("URLAUB", List.of("URLAUB/Sonderurlaub"), List.of("i976/A/FORTBILDUNG"));

    specialOrders.resolve();

    assertThat(specialOrders.getVacationCustomerorderId()).isEqualTo(1L);
    assertThat(specialOrders.isVacationDoNotCalculate(11L)).isTrue();
    assertThat(specialOrders.isRegularTraining(21L)).isTrue();
    assertThat(specialOrders.isRegularTraining(22L)).isFalse();
  }

  /** Every suborder of the vacation order outside the list is a yearly one, with an entitlement. */
  @Test
  void every_other_suborder_of_the_vacation_order_is_a_yearly_one() {
    configure("URLAUB", List.of("URLAUB/Sonderurlaub"), List.of());

    specialOrders.resolve();

    assertThat(specialOrders.isVacationYear(1L, 10L)).isTrue();
    assertThat(specialOrders.isVacationYear(1L, 11L)).isFalse();
    assertThat(specialOrders.isVacationYear(2L, 21L)).isFalse();
  }

  @Test
  void an_empty_setting_switches_the_role_off() {
    configure("", List.of(), List.of());

    specialOrders.resolve();

    assertThat(specialOrders.getVacationCustomerorderId()).isNull();
    assertThat(specialOrders.isVacationYear(1L, 10L)).isFalse();
    assertThat(specialOrders.isRegularTraining(21L)).isFalse();
    assertThat(specialOrders.isLockedCustomerorder(1L)).isFalse();
  }

  @Test
  void a_sign_that_names_no_order_stops_the_start() {
    configure("URLAUB", List.of(), List.of("i976/FORTBILDUNG"));

    assertThatThrownBy(specialOrders::resolve)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("salat.training.regular-suborder-signs")
        .hasMessageContaining("i976/FORTBILDUNG");
  }

  @Test
  void a_vacation_sign_that_names_no_order_stops_the_start() {
    configure("HOLIDAY", List.of(), List.of());

    assertThatThrownBy(specialOrders::resolve)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("salat.vacation.customerorder-sign");
  }

  @Test
  void an_entry_without_entitlement_outside_the_vacation_order_stops_the_start() {
    configure("URLAUB", List.of("i976/B"), List.of());

    assertThatThrownBy(specialOrders::resolve)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not lie below the vacation order");
  }

  /** Renaming anything above a configured suborder changes its complete sign, so all of it is locked. */
  @Test
  void locks_the_configured_suborders_and_everything_above_them() {
    configure("URLAUB", List.of("URLAUB/Sonderurlaub"), List.of("i976/A/FORTBILDUNG"));

    specialOrders.resolve();

    assertThat(specialOrders.isLockedCustomerorder(1L)).isTrue();
    assertThat(specialOrders.isLockedCustomerorder(2L)).isTrue();
    assertThat(specialOrders.isLockedSuborder(11L)).isTrue();
    assertThat(specialOrders.isLockedSuborder(21L)).isTrue();
    assertThat(specialOrders.isLockedSuborder(20L)).isTrue();
    // a yearly suborder and an unrelated branch stay free
    assertThat(specialOrders.isLockedSuborder(10L)).isFalse();
    assertThat(specialOrders.isLockedSuborder(22L)).isFalse();
  }

  private void configure(String vacationSign, List<String> doNotCalculate, List<String> regularTraining) {
    properties.getVacation().setCustomerorderSign(vacationSign);
    properties.getVacation().setDoNotCalculateSigns(doNotCalculate);
    properties.getTraining().setRegularSuborderSigns(regularTraining);
  }

  private static Customerorder customerorder(long id, String sign) {
    var customerorder = new Customerorder();
    setField(customerorder, "id", id);
    customerorder.setSign(sign);
    return customerorder;
  }

  private static Suborder suborder(long id, Customerorder customerorder, Suborder parent, String sign) {
    var suborder = new Suborder();
    setField(suborder, "id", id);
    suborder.setCustomerorder(customerorder);
    suborder.setParentorder(parent);
    suborder.setSign(sign);
    suborder.setFromDate(LocalDate.of(2026, 1, 1));
    return suborder;
  }
}
