package de.hbt.salat.budget.viewhelper;

import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.viewhelper.CustomerorderViewHelper;

/**
 * One entry of the customer order filter in the list views of the budget module (#949). Shaped like
 * every other order select (→ ADR-0017): the sign and the short description on the first line, the
 * customer underneath as {@code data-subtext}.
 *
 * <p>Built from the orders the records refer to by id (#1212). A hidden or expired order is labelled
 * as usual — those are the entries one is looking for when tidying up.
 *
 * @param id            the value of the option — the filter carries the id, not the sign (#1334)
 * @param customerLabel {@code null} when there is no customer to name; the attribute is then left
 *                      out entirely
 * @param hide          whether the order is hidden, so the option can say so like every other select
 */
public record CustomerorderFilterOption(long id, String sign, String label, String customerLabel, boolean hide) {

  public static CustomerorderFilterOption of(CustomerorderOption customerorder,
      CustomerorderViewHelper customerorderViewHelper) {
    return new CustomerorderFilterOption(customerorder.id(), customerorder.sign(), customerorderViewHelper.label(customerorder),
        customerorderViewHelper.customerLabel(customerorder), Boolean.TRUE.equals(customerorder.hide()));
  }

}
