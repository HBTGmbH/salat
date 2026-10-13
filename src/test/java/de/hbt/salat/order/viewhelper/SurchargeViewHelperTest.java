package de.hbt.salat.order.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static de.hbt.salat.order.domain.SurchargeKind.HOLIDAY;
import static de.hbt.salat.order.domain.SurchargeKind.NIGHT;
import static de.hbt.salat.order.domain.SurchargeKind.SATURDAY;
import static de.hbt.salat.order.domain.SurchargeKind.SUNDAY;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Locale;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SurchargeRate;

/**
 * The flag of an order or suborder with a time surcharge in the lists (#1469): its title names every
 * rate above 0, in the order of the form; without one the flag is not there.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SurchargeViewHelperTest {

  /** The bundle keeps number and percent sign together. */
  private static final String NBSP = "\u00a0";

  private final SurchargeViewHelper viewHelper = new SurchargeViewHelper(messages());

  @Test
  void the_badge_of_a_booking_shows_the_credit_and_names_kind_and_rate_in_its_title() {
    assertThat(viewHelper.credit(Duration.ofMinutes(150))).isEqualTo("+2:30");
    assertThat(viewHelper.title(SUNDAY, 100, Duration.ofMinutes(150))).isEqualTo("Zuschlag: Sonntag 100" + NBSP + "% +2:30");
    assertThat(viewHelper.named(Duration.ofMinutes(150))).isEqualTo("Zuschlag +2:30");
  }

  @Test
  void the_order_names_its_rates_above_zero() {
    var order = order();
    order.setSurchargeRate(NIGHT, new SurchargeRate(25, LocalTime.of(20, 0), LocalTime.of(6, 0)));
    order.setSurchargeRate(SATURDAY, SurchargeRate.of(0));
    order.setSurchargeRate(SUNDAY, SurchargeRate.of(50));

    assertThat(viewHelper.summary(order)).isEqualTo("Zeitzuschläge: Nacht 25" + NBSP + "%, 20:00–6:00 Uhr; Sonntag 50" + NBSP + "%");
  }

  @Test
  void an_order_without_a_rate_above_zero_has_no_flag() {
    var order = order();
    order.setSurchargeRate(HOLIDAY, SurchargeRate.of(0));

    assertThat(viewHelper.summary(order)).isEmpty();
  }

  @Test
  void the_suborder_names_what_it_offers_inherited_rates_included() {
    var order = order();
    order.setSurchargeRate(SATURDAY, SurchargeRate.of(50));
    order.setSurchargeRate(HOLIDAY, SurchargeRate.of(100));
    var suborder = suborder(order);
    suborder.setSurchargeRate(HOLIDAY, SurchargeRate.of(0));

    assertThat(viewHelper.summary(suborder)).isEqualTo("Zeitzuschläge: Samstag 50" + NBSP + "%");
  }

  @Test
  void standby_offers_no_surcharge_and_has_no_flag() {
    var order = order();
    order.setSurchargeRate(SATURDAY, SurchargeRate.of(50));
    var suborder = suborder(order);
    suborder.setOrderType(OrderType.BEREITSCHAFT);

    assertThat(viewHelper.summary(suborder)).isEmpty();
  }

  private static Customerorder order() {
    var order = new Customerorder();
    order.setOrderType(OrderType.STANDARD);
    return order;
  }

  private static Suborder suborder(Customerorder order) {
    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    return suborder;
  }

  private static MessageSourceAccessor messages() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    return new MessageSourceAccessor(messageSource, Locale.GERMANY);
  }
}
