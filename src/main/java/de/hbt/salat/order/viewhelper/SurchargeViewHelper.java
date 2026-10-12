package de.hbt.salat.order.viewhelper;

import de.hbt.salat.order.domain.SurchargeKind;
import de.hbt.salat.order.domain.SurchargeRate;
import de.hbt.salat.order.domain.SurchargeRateSource;
import de.hbt.salat.common.util.DurationUtils;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
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

  /** The flag with the credit it earns, "Nacht 25 % · +1:00" — the reviews and the daily view. */
  public String flagWithCredit(SurchargeKind kind, Integer percent, Duration credit) {
    if (kind == null) {
      return "";
    }
    return messages.getMessage("main.surcharge.flag.credit",
        new Object[] {flag(kind, percent), DurationUtils.formatSigned(credit != null ? credit : Duration.ZERO)});
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
