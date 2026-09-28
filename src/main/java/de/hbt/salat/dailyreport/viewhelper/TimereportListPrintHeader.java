package de.hbt.salat.dailyreport.viewhelper;

import static java.util.stream.Collectors.joining;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import org.springframework.context.support.MessageSourceAccessor;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.dailyreport.domain.TimereportFilterSummary;
import de.hbt.salat.dailyreport.domain.TimereportListFilter.Billable;

/**
 * The lines in the page margin of a printed booking list (#1147): title with period, a short line saying how much the
 * filter narrows, and when the list was read.
 *
 * <p>The short line counts and never names. A page margin box is only as high as the margin; a longer text wraps
 * towards the edge of the paper, where printers cut. The names stand in the block on the first page. Counting also
 * keeps personal names out of a stylesheet.
 *
 * <p>The time matters on paper: bookings keep changing, and a printout without it cannot say which state it shows.
 * It is the time the list was rendered — a filter change swaps the fragment and renders it anew.
 *
 * @param title      {@code Buchungsliste · 01.09.2026 – 26.09.2026}
 * @param filterLine {@code gefiltert: 3 Mitarbeiter, 2 Aufträge}, or the word for no filter at all
 * @param asOf       {@code 27.09.2026 12:05}
 */
public record TimereportListPrintHeader(String title, String filterLine, String asOf) {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  public static TimereportListPrintHeader from(TimereportFilterSummary summary, Billable billable, LocalDate from,
      LocalDate until, MessageSourceAccessor messages) {

    var title = messages.getMessage("main.timereportlist.title") + " · " + DATE.format(from) + " – "
        + DATE.format(until);

    var parts = new ArrayList<String>();
    count(parts, summary.employees().size(), "main.timereportlist.print.short.employees", messages);
    count(parts, summary.customers().size(), "main.timereportlist.print.short.customers", messages);
    count(parts, summary.orderCount(), "main.timereportlist.print.short.orders", messages);
    count(parts, summary.tickets().size(), "main.timereportlist.print.short.tickets", messages);
    switch (billable) {
      case BILLABLE -> parts.add(messages.getMessage("main.timereportlist.print.short.billable"));
      case NOT_BILLABLE -> parts.add(messages.getMessage("main.timereportlist.print.short.notbillable"));
      case ALL -> { /* no restriction */ }
    }

    var filterLine = parts.isEmpty()
        ? messages.getMessage("main.timereportlist.print.short.unfiltered")
        : messages.getMessage("main.timereportlist.print.short.filtered") + " " + parts.stream().collect(joining(", "));
    return new TimereportListPrintHeader(title, filterLine, DATE_TIME.format(ClockProvider.now()));
  }

  private static void count(ArrayList<String> parts, int count, String key, MessageSourceAccessor messages) {
    if (count > 0) {
      parts.add(messages.getMessage(key, new Object[] {count}));
    }
  }
}
