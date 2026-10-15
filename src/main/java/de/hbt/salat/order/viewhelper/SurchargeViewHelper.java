package de.hbt.salat.order.viewhelper;

import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SurchargeKind;
import de.hbt.salat.order.domain.SurchargeRate;
import de.hbt.salat.order.domain.SurchargeRateSource;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

/**
 * Time surcharge rates in words (#1469) — "Nacht 25 %", "25 %, 20:00–06:00 Uhr". One place, because the order
 * forms, the order lists and the booking form all name them; the credit a booking earns is shown by
 * {@code SurchargeCreditViewHelper} of the bookings.
 */
@Component
@RequiredArgsConstructor
public class SurchargeViewHelper {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  private final MessageSourceAccessor messages;

  public String kind(SurchargeKind kind) {
    return kind == null ? "" : messages.getMessage(kind.getLabel());
  }

  public String percent(Integer percent) {
    return percent == null ? "" : messages.getMessage("main.surcharge.percent", new Object[] {percent});
  }

  /** The kind with its rate, "Nacht 25 %" — a booking's flag and the choice in the booking form. */
  public String flag(SurchargeKind kind, Integer percent) {
    if (kind == null) {
      return "";
    }
    return messages.getMessage("main.surcharge.flag", new Object[] {kind(kind), percent(percent)});
  }

  /** The rate, with the night for a night rate: "25 %, 20:00–6:00 Uhr". */
  public String rate(SurchargeRate rate) {
    if (rate == null) {
      return "";
    }
    if (rate.nightFrom() == null || rate.nightUntil() == null) {
      return percent(rate.percent());
    }
    return messages.getMessage("main.surcharge.rate.night",
        new Object[] {percent(rate.percent()), time(rate.nightFrom()), time(rate.nightUntil())});
  }

  /**
   * The rates the order sets, "Zeitzuschläge: Nacht 25 %, 20:00–06:00 Uhr; Samstag 50 %" — the flag in
   * the order list. Empty where it sets none above 0 or its type earns none (standby, absences); rates only
   * its suborders set do not count here.
   */
  public String summary(Customerorder customerorder) {
    var orderType = customerorder.getOrderType();
    if (orderType != null && !orderType.earnsSurcharge()) {
      return "";
    }
    return summary(customerorder::getSurchargeRate);
  }

  /**
   * The rates a booking on the suborder may be flagged with, inherited ones included — the flag in the
   * suborder list. Empty where it offers none: no rate, 0 %, standby or an absence.
   */
  public String summary(Suborder suborder) {
    return summary(suborder::getOfferedSurchargeRate);
  }

  private String summary(Function<SurchargeKind, SurchargeRate> rates) {
    var offered = Arrays.stream(SurchargeKind.values())
        .map(kind -> {
          var rate = rates.apply(kind);
          return rate != null && rate.offered()
              ? messages.getMessage("main.surcharge.flag", new Object[] {kind(kind), rate(rate)})
              : null;
        })
        .filter(Objects::nonNull)
        .toList();
    return offered.isEmpty() ? "" : messages.getMessage("main.surcharge.summary", new Object[] {String.join("; ", offered)});
  }

  /**
   * The rates a suborder of the booking form offers, as its option carries them for the script of the form:
   * "NIGHT=25 SUNDAY=100", a kind not offered left out.
   */
  public String ratesAttribute(Map<SurchargeKind, SurchargeRate> offered) {
    return offered.entrySet().stream()
        .map(entry -> entry.getKey().name() + "=" + entry.getValue().percent())
        .collect(Collectors.joining(" "));
  }

  /** When the night counts on the suborder of the booking, a hint next to the flag; empty without a night rate. */
  public String nightHint(Map<SurchargeKind, SurchargeRate> offered) {
    var rate = offered.get(SurchargeKind.NIGHT);
    if (rate == null || rate.nightFrom() == null || rate.nightUntil() == null) {
      return "";
    }
    return messages.getMessage("main.surcharge.night.hint", new Object[] {time(rate.nightFrom()), time(rate.nightUntil())});
  }

  /** {@link #inherited(SurchargeRateSource)} of {@code kind}, as the suborder form asks for it per field. */
  public String inherited(Map<SurchargeKind, SurchargeRateSource> sources, SurchargeKind kind) {
    return sources == null ? "" : inherited(sources.get(kind));
  }

  /** What a suborder without a rate of its own inherits, and from where. */
  public String inherited(SurchargeRateSource source) {
    if (source == null) {
      return "";
    }
    if (source.rate() == null) {
      return messages.getMessage("main.surcharge.inherited.none");
    }
    var from = source.fromOrder()
        ? messages.getMessage("main.surcharge.source.order", new Object[] {source.sourceSign()})
        : messages.getMessage("main.surcharge.source.suborder", new Object[] {source.sourceSign()});
    return messages.getMessage("main.surcharge.inherited", new Object[] {rate(source.rate()), from});
  }

  private static String time(LocalTime time) {
    return TIME.format(time);
  }
}
