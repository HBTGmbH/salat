package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.order.domain.Customerorder;

/**
 * A rate references its order by sign and outlives it; nothing keeps the two validities in step
 * (#957). The list therefore says where they disagree — it never adjusts anything, because a rate
 * is billing ground.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class OrderPricingDeviationTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUN = LocalDate.of(2026, 6, 30);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);
  private static final LocalDate OPEN_END = LocalDate.of(2999, 12, 31);

  @Test
  public void reports_nothing_for_a_rate_that_stays_within_its_order() {
    var pricing = pricing(JAN, DEC);

    var deviation = deviationOf(pricing, order(JAN, DEC), pricing);

    assertThat(deviation.any()).isFalse();
  }

  @Test
  public void reports_a_rate_that_runs_beyond_the_end_of_its_order() {
    var pricing = pricing(JAN, DEC);

    var deviation = deviationOf(pricing, order(JAN, JUN), pricing);

    assertThat(deviation.endsAfterOrder()).isTrue();
    assertThat(deviation.startsBeforeOrder()).isFalse();
  }

  /** The stored 31.12.2999 is an open end, and an order that has ended is outrun by it. */
  @Test
  public void reports_an_open_rate_end_against_an_order_that_has_an_end() {
    var pricing = pricing(JAN, OPEN_END);

    assertThat(deviationOf(pricing, order(JAN, DEC), pricing).endsAfterOrder()).isTrue();
  }

  @Test
  public void reports_no_deviation_for_an_open_rate_end_against_an_open_order() {
    var pricing = pricing(JAN, OPEN_END);

    assertThat(deviationOf(pricing, order(JAN, null), pricing).any()).isFalse();
  }

  @Test
  public void reports_a_rate_that_starts_before_its_order() {
    var pricing = pricing(JAN, DEC);

    var deviation = deviationOf(pricing, order(JUN, DEC), pricing);

    assertThat(deviation.startsBeforeOrder()).isTrue();
    assertThat(deviation.endsAfterOrder()).isFalse();
  }

  @Test
  public void reports_an_order_period_that_the_order_wide_rates_leave_uncovered() {
    var pricing = pricing(JAN, JUN);

    var deviation = deviationOf(pricing, order(JAN, DEC), pricing);

    assertThat(deviation.uncoveredOrderPeriod()).isTrue();
  }

  /** A rate for one suborder is not asked to cover the order, so it is not accused of a gap. */
  @Test
  public void reports_no_gap_on_a_suborder_specific_rate() {
    var pricing = pricing(JAN, JUN);
    pricing.setSuborderSign("co/01/");

    var deviation = deviationOf(pricing, order(JAN, DEC), pricing);

    assertThat(deviation.uncoveredOrderPeriod()).isFalse();
    // Its own dates are still held against the order.
    assertThat(deviation.any()).isFalse();
  }

  @Test
  public void reports_no_gap_on_an_employee_specific_rate() {
    var pricing = pricing(JAN, JUN);
    pricing.setEmployeeSign("emp");

    assertThat(deviationOf(pricing, order(JAN, DEC), pricing).uncoveredOrderPeriod()).isFalse();
  }

  /** A rate outlives its order; with no order there is nothing to disagree with. */
  @Test
  public void reports_nothing_when_the_order_no_longer_exists() {
    var pricing = pricing(JAN, OPEN_END);

    assertThat(OrderPricingDeviation.of(pricing, null, OrderPricingLookup.of(List.of())).any())
        .isFalse();
  }

  private static OrderPricingDeviation deviationOf(OrderPricing pricing, Customerorder order,
                                                   OrderPricing... allRatesOfTheOrder) {
    return OrderPricingDeviation.of(pricing, order, OrderPricingLookup.of(List.of(allRatesOfTheOrder)));
  }

  private static OrderPricing pricing(LocalDate validFrom, LocalDate validUntil) {
    var pricing = new OrderPricing();
    pricing.setCustomerorderSign("co");
    pricing.setPriceCentsPerHour(10000);
    pricing.setValidFrom(validFrom);
    pricing.setValidUntil(validUntil);
    return pricing;
  }

  private static Customerorder order(LocalDate fromDate, LocalDate untilDate) {
    var order = new Customerorder();
    order.setSign("co");
    order.setFromDate(fromDate);
    order.setUntilDate(untilDate);
    return order;
  }

}
