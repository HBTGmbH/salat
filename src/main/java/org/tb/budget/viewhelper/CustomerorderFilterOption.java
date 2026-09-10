package org.tb.budget.viewhelper;

import org.tb.order.domain.Customerorder;
import org.tb.order.viewhelper.CustomerorderViewHelper;

/**
 * One entry of the customer order filter in the list views of the budget module (#949). Shaped like
 * every other order select (→ ADR-0017): the sign and the short description on the first line, the
 * customer underneath as {@code data-subtext}.
 *
 * <p>Built from a sign rather than from an order, because that is what these records store: they
 * refer to their order by sign and outlive it. When the order is hidden or expired it is labelled as
 * usual — those are the entries one is looking for when tidying up — and when it is gone
 * altogether, the bare sign stands on its own so the records stay reachable.
 *
 * @param customerLabel {@code null} when there is no customer to name; the attribute is then left
 *                      out entirely
 */
public record CustomerorderFilterOption(String sign, String label, String customerLabel) {

  public static CustomerorderFilterOption from(String sign, Customerorder customerorder,
      CustomerorderViewHelper customerorderViewHelper) {
    if (customerorder == null) {
      return new CustomerorderFilterOption(sign, sign, null);
    }
    var description = customerorder.getShortdescription();
    var label = description == null || description.isBlank() ? sign : sign + " - " + description;
    return new CustomerorderFilterOption(sign, label, customerorderViewHelper.customerLabel(customerorder));
  }

}
