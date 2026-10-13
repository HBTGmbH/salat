package de.hbt.salat.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static de.hbt.salat.order.domain.SurchargeKind.HOLIDAY;
import static de.hbt.salat.order.domain.SurchargeKind.NIGHT;
import static de.hbt.salat.order.domain.SurchargeKind.SATURDAY;
import static de.hbt.salat.order.domain.SurchargeKind.SUNDAY;

import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * Time surcharge rates (#1469): set per kind on the order, overridden per kind by a suborder, inherited
 * from the nearest suborder above with a rate, else from the order; the credit is duration × rate.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SurchargeRateTest {

  private static final LocalTime TWENTY = LocalTime.of(20, 0);
  private static final LocalTime SIX = LocalTime.of(6, 0);

  @Test
  void the_credit_is_duration_times_rate_per_kind() {
    assertThat(new SurchargeRate(25, TWENTY, SIX).creditFor(Duration.ofHours(4))).isEqualTo(Duration.ofHours(1));
    assertThat(SurchargeRate.of(50).creditFor(Duration.ofHours(3))).isEqualTo(Duration.ofMinutes(90));
    assertThat(SurchargeRate.of(100).creditFor(Duration.ofMinutes(75))).isEqualTo(Duration.ofMinutes(75));
  }

  @Test
  void the_credit_is_rounded_to_whole_minutes() {
    // 1:30 × 25 % = 22,5 minutes
    assertThat(SurchargeRate.of(25).creditFor(Duration.ofMinutes(90))).isEqualTo(Duration.ofMinutes(23));
    // 0:10 × 33 % = 3,3 minutes
    assertThat(SurchargeRate.of(33).creditFor(Duration.ofMinutes(10))).isEqualTo(Duration.ofMinutes(3));
  }

  @Test
  void a_rate_of_zero_is_not_offered() {
    assertThat(SurchargeRate.of(0).offered()).isFalse();
    assertThat(SurchargeRate.of(1).offered()).isTrue();
  }

  @Test
  void an_order_without_a_rate_offers_no_surcharge() {
    var suborder = suborder(new Customerorder(), null);

    assertThat(suborder.getEffectiveSurchargeRate(NIGHT)).isNull();
    assertThat(suborder.getOfferedSurchargeRate(NIGHT)).isNull();
  }

  @Test
  void a_suborder_without_a_rate_takes_the_orders() {
    var order = order();
    order.setSurchargeRate(NIGHT, new SurchargeRate(25, TWENTY, SIX));
    var suborder = suborder(order, null);

    assertThat(suborder.getSurchargeRate(NIGHT)).isNull();
    assertThat(suborder.getEffectiveSurchargeRate(NIGHT)).isEqualTo(new SurchargeRate(25, TWENTY, SIX));
  }

  @Test
  void the_nearest_suborder_above_with_a_rate_wins_over_the_order() {
    var order = order();
    order.setSurchargeRate(SATURDAY, SurchargeRate.of(50));
    var top = suborder(order, null);
    top.setSurchargeRate(SATURDAY, SurchargeRate.of(75));
    var middle = suborder(order, top);
    var bottom = suborder(order, middle);

    assertThat(bottom.getEffectiveSurchargeRate(SATURDAY)).isEqualTo(SurchargeRate.of(75));
    middle.setSurchargeRate(SATURDAY, SurchargeRate.of(60));
    assertThat(bottom.getEffectiveSurchargeRate(SATURDAY)).isEqualTo(SurchargeRate.of(60));
  }

  @Test
  void each_kind_inherits_on_its_own() {
    var order = order();
    order.setSurchargeRate(SATURDAY, SurchargeRate.of(50));
    order.setSurchargeRate(HOLIDAY, SurchargeRate.of(100));
    var suborder = suborder(order, null);
    suborder.setSurchargeRate(SATURDAY, SurchargeRate.of(75));

    assertThat(suborder.getEffectiveSurchargeRate(SATURDAY)).isEqualTo(SurchargeRate.of(75));
    assertThat(suborder.getEffectiveSurchargeRate(HOLIDAY)).isEqualTo(SurchargeRate.of(100));
  }

  @Test
  void zero_on_a_suborder_withdraws_the_orders_surcharge() {
    var order = order();
    order.setSurchargeRate(HOLIDAY, SurchargeRate.of(100));
    var suborder = suborder(order, null);
    suborder.setSurchargeRate(HOLIDAY, SurchargeRate.of(0));
    var below = suborder(order, suborder);

    assertThat(suborder.getOfferedSurchargeRate(HOLIDAY)).isNull();
    assertThat(below.getOfferedSurchargeRate(HOLIDAY)).isNull();
  }

  @Test
  void inheriting_again_drops_the_own_rate() {
    var order = order();
    order.setSurchargeRate(SATURDAY, SurchargeRate.of(50));
    var suborder = suborder(order, null);
    suborder.setSurchargeRate(SATURDAY, SurchargeRate.of(75));

    suborder.setSurchargeRate(SATURDAY, null);

    assertThat(suborder.getEffectiveSurchargeRate(SATURDAY)).isEqualTo(SurchargeRate.of(50));
  }

  @Test
  void standby_offers_no_surcharge() {
    var order = order();
    order.setSurchargeRate(NIGHT, new SurchargeRate(25, TWENTY, SIX));
    var standby = suborder(order, null);
    standby.setOrderType(OrderType.BEREITSCHAFT);

    assertThat(standby.getEffectiveSurchargeRate(NIGHT)).isNotNull();
    assertThat(standby.getOfferedSurchargeRate(NIGHT)).isNull();
  }

  /** What the suborder form shows under each field: the inherited rate and where it comes from. */
  @Test
  void the_source_of_an_inherited_rate() {
    var order = order();
    order.setSign("4711");
    order.setSurchargeRate(NIGHT, new SurchargeRate(25, TWENTY, SIX));
    var top = suborder(order, null);
    top.setSign("20");
    top.setSurchargeRate(NIGHT, new SurchargeRate(30, LocalTime.of(22, 0), SIX));
    var middle = suborder(order, top);
    middle.setSign("1");
    top.deriveCompleteOrderSign();
    middle.deriveCompleteOrderSign();

    var fromParent = SurchargeRateSource.inheritedBy(NIGHT, order, middle);
    assertThat(fromParent.rate()).isEqualTo(new SurchargeRate(30, LocalTime.of(22, 0), SIX));
    assertThat(fromParent.fromOrder()).isFalse();
    assertThat(fromParent.sourceSign()).isEqualTo("4711/20");

    var atTheTop = SurchargeRateSource.inheritedBy(NIGHT, order, null);
    assertThat(atTheTop.rate()).isEqualTo(new SurchargeRate(25, TWENTY, SIX));
    assertThat(atTheTop.fromOrder()).isTrue();
    assertThat(atTheTop.sourceSign()).isEqualTo("4711");

    assertThat(SurchargeRateSource.inheritedBy(SATURDAY, order, middle).rate()).isNull();
  }

  @Test
  void the_form_input_checks_range_and_night_times() {
    assertThat(new SurchargeInput(25, TWENTY, SIX, 50, null, 100).percentsInRange()).isTrue();
    assertThat(new SurchargeInput(null, null, null, -1, null, null).percentsInRange()).isFalse();
    assertThat(new SurchargeInput(null, null, null, null, 1001, null).percentsInRange()).isFalse();
    assertThat(new SurchargeInput(null, null, null, null, null, 1001).percentsInRange()).isFalse();

    assertThat(new SurchargeInput(25, TWENTY, SIX, null, null, null).nightTimesComplete()).isTrue();
    assertThat(new SurchargeInput(25, null, SIX, null, null, null).nightTimesComplete()).isFalse();
    assertThat(new SurchargeInput(25, SIX, SIX, null, null, null).nightTimesComplete()).isFalse();
    // without a night surcharge the times do not matter
    assertThat(new SurchargeInput(null, null, null, null, null, null).nightTimesComplete()).isTrue();
    assertThat(new SurchargeInput(0, null, null, null, null, null).nightTimesComplete()).isTrue();
  }

  @Test
  void the_form_input_becomes_the_stored_rates() {
    var input = new SurchargeInput(0, TWENTY, SIX, null, 75, 100);

    assertThat(input.rate(NIGHT)).isEqualTo(SurchargeRate.of(0));
    assertThat(input.rate(SATURDAY)).isNull();
    assertThat(input.rate(SUNDAY)).isEqualTo(SurchargeRate.of(75));
    assertThat(input.rate(HOLIDAY)).isEqualTo(SurchargeRate.of(100));
    assertThat(new SurchargeInput(25, TWENTY, SIX, null, null, null).rate(NIGHT)).isEqualTo(new SurchargeRate(25, TWENTY, SIX));
  }

  @Test
  void the_stored_rates_read_back_for_the_form() {
    var order = order();
    order.setSurchargeRate(NIGHT, new SurchargeRate(25, TWENTY, SIX));
    order.setSurchargeRate(SATURDAY, SurchargeRate.of(50));
    order.setSurchargeRate(SUNDAY, SurchargeRate.of(75));
    order.setSurchargeRate(HOLIDAY, SurchargeRate.of(100));

    assertThat(SurchargeInput.of(order::getSurchargeRate)).isEqualTo(new SurchargeInput(25, TWENTY, SIX, 50, 75, 100));
  }

  private static Customerorder order() {
    var order = new Customerorder();
    order.setOrderType(OrderType.STANDARD);
    return order;
  }

  private static Suborder suborder(Customerorder order, Suborder parent) {
    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    suborder.setParentorder(parent);
    return suborder;
  }
}
