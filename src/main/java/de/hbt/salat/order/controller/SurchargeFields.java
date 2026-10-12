package de.hbt.salat.order.controller;

import de.hbt.salat.order.domain.SurchargeInput;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * The time surcharge fields of the order and the suborder form (#1469), bound as {@code surcharges.*}.
 * An empty percent means no surcharge on the order and inherit on a suborder.
 */
@Getter
@Setter
public class SurchargeFields {

  private Integer nightPercent;
  @DateTimeFormat(pattern = "HH:mm")
  private LocalTime nightFrom;
  @DateTimeFormat(pattern = "HH:mm")
  private LocalTime nightUntil;
  private Integer weekendPercent;
  private Integer holidayPercent;

  public SurchargeInput toInput() {
    return new SurchargeInput(nightPercent, nightFrom, nightUntil, weekendPercent, holidayPercent);
  }

  public static SurchargeFields of(SurchargeInput input) {
    var fields = new SurchargeFields();
    fields.nightPercent = input.nightPercent();
    fields.nightFrom = input.nightFrom();
    fields.nightUntil = input.nightUntil();
    fields.weekendPercent = input.weekendPercent();
    fields.holidayPercent = input.holidayPercent();
    return fields;
  }
}
