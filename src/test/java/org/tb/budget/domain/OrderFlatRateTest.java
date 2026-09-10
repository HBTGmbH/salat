package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The schedule a flat rate amounts to (#972). One definition, many due dates — that is what keeps a
 * maintenance contract a single record instead of twelve rows a year, and it is the only place the
 * dates are derived: the form preview and the controlling both ask this method, so neither can
 * arrive at a different calendar.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class OrderFlatRateTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);

  @Test
  public void a_one_off_flat_rate_is_due_on_its_start() {
    var rate = flatRate(FlatRateRhythm.ONCE, JAN, JAN, "1000");

    assertThat(rate.dueAmountsWithin(JAN, DEC))
        .extracting(FlatRateDueAmount::due).containsExactly(JAN);
  }

  @Test
  public void a_monthly_flat_rate_is_due_every_month_of_its_validity() {
    var rate = flatRate(FlatRateRhythm.MONTHLY, JAN, DEC, "100");

    assertThat(rate.dueAmountsWithin(JAN, DEC)).hasSize(12);
    assertThat(total(rate, JAN, DEC)).isEqualByComparingTo("1200");
  }

  /**
   * Every date is computed from the start rather than from its predecessor. Stepping month by month
   * would clamp January the 31st to February the 28th and then carry that day forward for good, so
   * March would be due on the 28th as well.
   */
  @Test
  public void a_monthly_flat_rate_keeps_the_day_of_month_after_a_short_month() {
    var rate = flatRate(FlatRateRhythm.MONTHLY, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 4, 30), "100");

    assertThat(rate.dueAmountsWithin(JAN, DEC)).extracting(FlatRateDueAmount::due)
        .containsExactly(
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 31),
            LocalDate.of(2026, 4, 30));
  }

  @Test
  public void a_monthly_flat_rate_ends_with_its_validity() {
    var rate = flatRate(FlatRateRhythm.MONTHLY, JAN, LocalDate.of(2026, 3, 15), "100");

    assertThat(rate.dueAmountsWithin(JAN, DEC)).extracting(FlatRateDueAmount::due)
        .containsExactly(JAN, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
  }

  /** The span the caller asks about, not the validity, decides what comes back. */
  @Test
  public void only_the_amounts_inside_the_requested_span_are_returned() {
    var rate = flatRate(FlatRateRhythm.MONTHLY, JAN, DEC, "100");

    assertThat(rate.dueAmountsWithin(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31))).hasSize(3);
  }

  @Test
  public void a_span_ending_before_the_validity_starts_yields_nothing() {
    var rate = flatRate(FlatRateRhythm.MONTHLY, LocalDate.of(2026, 6, 1), DEC, "100");

    assertThat(rate.dueAmountsWithin(JAN, LocalDate.of(2026, 3, 31))).isEmpty();
  }

  /** Instalments carry an amount each, so the definition's own amount says nothing. */
  @Test
  public void instalments_are_due_on_their_own_dates_with_their_own_amounts() {
    var rate = flatRate(FlatRateRhythm.INSTALMENTS, JAN, DEC, null);
    addInstalment(rate, LocalDate.of(2026, 9, 1), "2000");
    addInstalment(rate, LocalDate.of(2026, 3, 1), "1000");

    assertThat(rate.dueAmountsWithin(JAN, DEC)).extracting(FlatRateDueAmount::due)
        .containsExactly(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 1));
    assertThat(total(rate, JAN, DEC)).isEqualByComparingTo("3000");
  }

  @Test
  public void an_instalment_outside_the_requested_span_is_left_out() {
    var rate = flatRate(FlatRateRhythm.INSTALMENTS, JAN, DEC, null);
    addInstalment(rate, LocalDate.of(2026, 9, 1), "2000");

    assertThat(rate.dueAmountsWithin(JAN, LocalDate.of(2026, 6, 30))).isEmpty();
  }

  /** A definition without instalments yet earns nothing — the case the list view marks. */
  @Test
  public void a_flat_rate_billed_in_instalments_without_any_yields_nothing() {
    var rate = flatRate(FlatRateRhythm.INSTALMENTS, JAN, DEC, null);

    assertThat(rate.dueAmountsWithin(JAN, DEC)).isEmpty();
  }

  @Test
  public void an_order_wide_flat_rate_has_no_first_level_scope() {
    var rate = flatRate(FlatRateRhythm.ONCE, JAN, JAN, "100");

    assertThat(rate.isOrderWide()).isTrue();
    assertThat(rate.firstLevelSign()).isNull();
  }

  /** A flat rate deep below the first level belongs to the level a plan can live on. */
  @Test
  public void a_deep_flat_rate_resolves_to_its_first_level_scope() {
    var rate = flatRate(FlatRateRhythm.ONCE, JAN, JAN, "100");
    rate.setSuborderSign("co/01/D");

    assertThat(rate.isOrderWide()).isFalse();
    assertThat(rate.firstLevelSign()).isEqualTo("co/01");
  }

  private static BigDecimal total(OrderFlatRate rate, LocalDate from, LocalDate until) {
    return rate.dueAmountsWithin(from, until).stream().map(FlatRateDueAmount::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static void addInstalment(OrderFlatRate rate, LocalDate due, String amount) {
    var instalment = new OrderFlatRateInstalment();
    instalment.setOrderFlatRate(rate);
    instalment.setDue(due);
    instalment.setAmount(new BigDecimal(amount));
    rate.getInstalments().add(instalment);
  }

  private static OrderFlatRate flatRate(FlatRateRhythm rhythm, LocalDate from, LocalDate until, String amount) {
    var rate = new OrderFlatRate();
    rate.setCustomerorderSign("co");
    rate.setRhythm(rhythm);
    rate.setValidFrom(from);
    rate.setValidUntil(until);
    rate.setAmount(amount == null ? null : new BigDecimal(amount));
    return rate;
  }

}
