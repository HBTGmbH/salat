package de.hbt.salat.order.controller;

import de.hbt.salat.order.domain.SurchargeInput;
import de.hbt.salat.order.domain.SurchargeRate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.BindingResult;

/**
 * The time surcharge fields of the order and the suborder form (#1469), bound as {@code surchargeRates.*}.
 * An empty percent means no surcharge on the order and inherit on a suborder. A form object of its own
 * rather than {@link SurchargeInput} bound directly: the binder fills nested fields through setters, and
 * the times need their pattern.
 */
@Getter
@Setter
public class SurchargeFields {

  private static final String PATH = "surchargeRates.";

  private Integer nightPercent;
  @DateTimeFormat(pattern = "HH:mm")
  private LocalTime nightFrom;
  @DateTimeFormat(pattern = "HH:mm")
  private LocalTime nightUntil;
  private Integer saturdayPercent;
  private Integer sundayPercent;
  private Integer holidayPercent;

  public SurchargeInput toInput() {
    return new SurchargeInput(nightPercent, nightFrom, nightUntil, saturdayPercent, sundayPercent, holidayPercent);
  }

  public static SurchargeFields of(SurchargeInput input) {
    var fields = new SurchargeFields();
    fields.nightPercent = input.nightPercent();
    fields.nightFrom = input.nightFrom();
    fields.nightUntil = input.nightUntil();
    fields.saturdayPercent = input.saturdayPercent();
    fields.sundayPercent = input.sundayPercent();
    fields.holidayPercent = input.holidayPercent();
    return fields;
  }

  /**
   * The fields of an order: where it pays no night surcharge, the times start out as the usual night, so that
   * entering a percent is all it takes. A suborder inherits its night and gets none.
   */
  public static SurchargeFields ofOrder(SurchargeInput input) {
    var fields = of(input);
    if (fields.nightFrom == null && fields.nightUntil == null) {
      fields.nightFrom = SurchargeRate.USUAL_NIGHT_FROM;
      fields.nightUntil = SurchargeRate.USUAL_NIGHT_UNTIL;
    }
    return fields;
  }

  /**
   * The rules of {@link SurchargeInput#applyTo} as field errors, so that the form marks the field rather than
   * reporting above the form; a time the binder could not read carries its own error already.
   */
  public void validate(BindingResult bindingResult, MessageSourceAccessor messages) {
    rejectOutOfRange(bindingResult, messages, "nightPercent", nightPercent);
    rejectOutOfRange(bindingResult, messages, "saturdayPercent", saturdayPercent);
    rejectOutOfRange(bindingResult, messages, "sundayPercent", sundayPercent);
    rejectOutOfRange(bindingResult, messages, "holidayPercent", holidayPercent);
    if (toInput().nightTimesComplete()) {
      return;
    }
    rejectMissingTime(bindingResult, messages, "nightFrom", nightFrom);
    rejectMissingTime(bindingResult, messages, "nightUntil", nightUntil);
    if (nightFrom != null && nightFrom.equals(nightUntil)) {
      bindingResult.rejectValue(PATH + "nightUntil", "error.surcharge.night",
          messages.getMessage("form.surcharge.error.night.equal"));
    }
  }

  private static void rejectOutOfRange(BindingResult bindingResult, MessageSourceAccessor messages, String field,
      Integer percent) {
    if (!SurchargeInput.isPercentInRange(percent)) {
      var highest = String.valueOf(SurchargeInput.MAX_PERCENT);
      bindingResult.rejectValue(PATH + field, "error.surcharge.percent",
          messages.getMessage("form.surcharge.error.percent", new Object[] {highest}));
    }
  }

  private static void rejectMissingTime(BindingResult bindingResult, MessageSourceAccessor messages, String field,
      LocalTime value) {
    if (value == null && !bindingResult.hasFieldErrors(PATH + field)) {
      bindingResult.rejectValue(PATH + field, "error.surcharge.night",
          messages.getMessage("form.surcharge.error.night.missing"));
    }
  }
}
