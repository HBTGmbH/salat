package de.hbt.salat.common.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.Getter;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;

/**
 * A customer order or a suborder carries a new complete sign (#1206): an order was renamed, or a
 * suborder was renamed or moved to another parent or another order. The suborders below follow:
 * their complete signs start with the one that changed.
 *
 * <p>Signs stay changeable (#1206, ADR-0034). Everything inside the application refers to orders and
 * suborders by id and follows on its own; this event is for what still names them by sign — the
 * suborder pattern of a customer rate, and the SQL of report and ETL definitions. In {@code common}
 * because modules react to it that may not import {@code order}; it carries plain values only.
 *
 * <p>Published after the order or suborder is saved, inside the same transaction. A listener adds a
 * notice for whatever it could not follow by itself; the person saving reads them below the success
 * message.
 */
@Getter
public class SignsRenamedEvent {

  /** The complete sign before, {@code ORDER} or {@code ORDER/01/02}. */
  private final String oldSign;

  /** The complete sign after. */
  private final String newSign;

  private final long customerorderIdBefore;

  private final long customerorderIdAfter;

  private final List<ServiceFeedbackMessage> notices = new ArrayList<>();

  public SignsRenamedEvent(String oldSign, String newSign, long customerorderIdBefore, long customerorderIdAfter) {
    this.oldSign = Objects.requireNonNull(oldSign);
    this.newSign = Objects.requireNonNull(newSign);
    this.customerorderIdBefore = customerorderIdBefore;
    this.customerorderIdAfter = customerorderIdAfter;
  }

  public void addNotice(ServiceFeedbackMessage notice) {
    notices.add(notice);
  }

  /** Whether a suborder moved to another customer order, taking its branch along. */
  public boolean isMovedToAnotherOrder() {
    return customerorderIdBefore != customerorderIdAfter;
  }

  /**
   * The value with the old sign replaced by the new one, where it names the renamed order or
   * suborder as a whole: the old sign itself, or the old sign followed by a slash and what lies
   * below it. {@code null} for anything else — a value that merely starts with the same characters
   * ({@code ORDER/010} under a renamed {@code ORDER/01}) names a different suborder.
   */
  public String renamed(String value) {
    if (value == null) return null;
    if (value.equals(oldSign)) return newSign;
    if (value.startsWith(oldSign + "/")) return newSign + value.substring(oldSign.length());
    return null;
  }

  /**
   * Whether an SQL text names the old sign as a literal — a quote, the sign, and then a quote, a
   * slash or a {@code %}. Report and ETL definitions are free SQL; nothing rewrites them, the person
   * saving is told instead. A bare substring would hit every number that happens to contain the
   * sign.
   */
  public boolean isMentionedIn(String sql) {
    if (sql == null) return false;
    return Pattern.compile("'" + Pattern.quote(oldSign) + "['/%]").matcher(sql).find();
  }
}
