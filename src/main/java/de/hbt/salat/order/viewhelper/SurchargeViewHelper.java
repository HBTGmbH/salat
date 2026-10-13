package de.hbt.salat.order.viewhelper;

import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SurchargeKind;
import de.hbt.salat.order.domain.SurchargeRate;
import de.hbt.salat.order.domain.SurchargeRateSource;
import de.hbt.salat.common.util.DurationUtils;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

/**
 * Time surcharges in words (#1469) — "Nacht 25 %", "25 %, 20:00–6:00 Uhr". One place, because the order
 * forms, the booking form, the reviews and the booking list all name them.
 */
@Component
@RequiredArgsConstructor
public class SurchargeViewHelper {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

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

  /** The credit on the time account with its sign, "+1:30" — the matrix row and the sums. */
  public String credit(Duration credit) {
    return DurationUtils.formatSigned(credit != null ? credit : Duration.ZERO);
  }

  /**
   * The title of a flagged booking's badge, "Zuschlag: Sonntag 100 % +2:30". The badge itself shows
   * nothing but the credit ({@link #credit}): what counts is how much it adds to the time account.
   */
  public String title(SurchargeKind kind, Integer percent, Duration credit) {
    return messages.getMessage("main.surcharge.badge.title", new Object[] {flag(kind, percent), credit(credit)});
  }

  /** The credit named in running text, "Zuschlag +2:30" — the details of a matrix cell. */
  public String named(Duration credit) {
    return messages.getMessage("main.surcharge.named", new Object[] {credit(credit)});
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
   * The rates the order sets, "Zeitzuschläge: Nacht 25 %, 20:00–6:00 Uhr; Samstag 50 %" — the flag in
   * the order list. Empty where it sets none above 0; rates only its suborders set do not count here.
   */
  public String summary(Customerorder customerorder) {
    return summary(customerorder::getSurchargeRate);
  }

  /**
   * The rates a booking on the suborder may be flagged with, inherited ones included — the flag in the
   * suborder list. Empty where it offers none: no rate, 0 % or standby.
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

  /** When the night counts on the suborder of the booking, a hint next to the flag. */
  public String nightHint(SurchargeRate rate) {
    if (rate == null || rate.nightFrom() == null || rate.nightUntil() == null) {
      return "";
    }
    return messages.getMessage("main.surcharge.night.hint", new Object[] {time(rate.nightFrom()), time(rate.nightUntil())});
  }

  /** {@link #inherited(SurchargeRateSource)} of {@code kind}, by name, as the suborder form asks. */
  public String inherited(Map<SurchargeKind, SurchargeRateSource> sources, String kind) {
    return sources == null ? "" : inherited(sources.get(SurchargeKind.valueOf(kind)));
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
