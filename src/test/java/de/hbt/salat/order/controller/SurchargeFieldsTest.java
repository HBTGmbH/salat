package de.hbt.salat.order.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.Locale;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.BeanPropertyBindingResult;
import de.hbt.salat.order.domain.SurchargeInput;

/**
 * The time surcharge fields of the order and the suborder form (#1469): a rate out of range and a night rate
 * without its night are marked at the field they concern, not reported above the form.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SurchargeFieldsTest {

  private final MessageSourceAccessor messages = messages();

  @Test
  void a_rate_out_of_range_is_marked_at_its_field() {
    var form = new CustomerorderForm();
    form.getSurchargeRates().setSundayPercent(SurchargeInput.MAX_PERCENT + 1);

    var result = validate(form);

    assertThat(result.getFieldError("surchargeRates.sundayPercent").getDefaultMessage())
        .isEqualTo("Bitte einen Satz zwischen 0 und 1000 % angeben.");
    assertThat(result.getFieldErrorCount()).isOne();
  }

  @Test
  void a_night_rate_marks_each_missing_time() {
    var form = new CustomerorderForm();
    form.getSurchargeRates().setNightPercent(25);
    form.getSurchargeRates().setNightFrom(LocalTime.of(22, 0));

    var result = validate(form);

    assertThat(result.hasFieldErrors("surchargeRates.nightFrom")).isFalse();
    assertThat(result.getFieldError("surchargeRates.nightUntil").getDefaultMessage())
        .isEqualTo("Ein Nachtsatz braucht Beginn und Ende der Nacht.");
  }

  @Test
  void a_night_that_begins_where_it_ends_is_marked_at_its_end() {
    var form = new CustomerorderForm();
    form.getSurchargeRates().setNightPercent(25);
    form.getSurchargeRates().setNightFrom(LocalTime.of(22, 0));
    form.getSurchargeRates().setNightUntil(LocalTime.of(22, 0));

    assertThat(validate(form).getFieldError("surchargeRates.nightUntil").getDefaultMessage())
        .isEqualTo("Beginn und Ende der Nacht müssen verschieden sein.");
  }

  @Test
  void without_a_night_rate_the_times_need_not_be_there() {
    var form = new CustomerorderForm();
    form.getSurchargeRates().setNightPercent(0);
    form.getSurchargeRates().setSaturdayPercent(50);

    assertThat(validate(form).hasErrors()).isFalse();
  }

  @Test
  void an_order_without_a_night_starts_with_the_usual_one_a_suborder_does_not() {
    var order = SurchargeFields.ofOrder(SurchargeInput.NONE);
    var suborder = SurchargeFields.of(SurchargeInput.NONE);

    assertThat(order.getNightFrom()).isEqualTo(LocalTime.of(20, 0));
    assertThat(order.getNightUntil()).isEqualTo(LocalTime.of(6, 0));
    assertThat(suborder.getNightFrom()).isNull();
  }

  private BeanPropertyBindingResult validate(CustomerorderForm form) {
    var result = new BeanPropertyBindingResult(form, "customerorderForm");
    form.getSurchargeRates().validate(result, messages);
    return result;
  }

  private static MessageSourceAccessor messages() {
    var messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("de/hbt/salat/web/MessageResources");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);
    return new MessageSourceAccessor(messageSource, Locale.GERMANY);
  }
}
