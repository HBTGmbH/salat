package de.hbt.salat.order.viewhelper;

import org.springframework.stereotype.Component;
import de.hbt.salat.customer.viewhelper.CustomerLabelViewHelper;
import de.hbt.salat.order.domain.Customerorder;

/**
 * Labels for a customer order in dropdowns (#1266, → ADR-0017).
 *
 * <p>Every order select in the application shows {@code Kennung - Kurzbeschreibung}, with the
 * customer underneath in the shape {@link CustomerLabelViewHelper} gives it everywhere. The
 * customer stands only there, never in the option text as well.
 *
 * <p>Used from templates as {@code ${@customerorderViewHelper.label(co)}} and
 * {@code ${@customerorderViewHelper.customerLabel(co)}}; salat.js renders a {@code data-subtext}
 * attribute as the second line of the option and searches it too, so the customer becomes findable
 * in an order select.
 */
@Component
public class CustomerorderViewHelper {

    public String label(Customerorder customerorder) {
        return customerorder == null ? null : of(customerorder.getSign(), customerorder.getShortdescription());
    }

    /** {@code null} when there is no customer to name — the attribute is then left out entirely. */
    public String customerLabel(Customerorder customerorder) {
        return customerOf(customerorder);
    }

    /** {@link #customerLabel(Customerorder)} for code that has no instance at hand. */
    public static String customerOf(Customerorder customerorder) {
        if (customerorder == null || customerorder.getCustomer() == null) {
            return null;
        }
        return CustomerLabelViewHelper.of(customerorder.getCustomer().getShortname(), customerorder.getCustomer().getName());
    }

    /** Sign and short description, the bare sign where there is no description. */
    public static String of(String sign, String shortdescription) {
        return shortdescription == null || shortdescription.isBlank() ? sign : sign + " - " + shortdescription;
    }

}
