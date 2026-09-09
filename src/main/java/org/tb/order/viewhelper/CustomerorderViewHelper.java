package org.tb.order.viewhelper;

import org.springframework.stereotype.Component;
import org.tb.order.domain.Customerorder;

/**
 * Labels for a customer order in dropdowns (→ ADR-0017).
 *
 * <p>Every order select in the application shows the customer underneath the order, in the same
 * shape as the order line above it: short name, separator, full name. One place for it, because the
 * alternative was the same concatenation with the same null guard in eight templates.
 *
 * <p>Used from templates as {@code ${@customerorderViewHelper.customerLabel(co)}}; salat.js renders
 * a {@code data-subtext} attribute as the second line of the option and searches it too, so the
 * customer becomes findable in an order select.
 */
@Component
public class CustomerorderViewHelper {

    /** {@code null} when there is no customer to name — the attribute is then left out entirely. */
    public String customerLabel(Customerorder customerorder) {
        if (customerorder == null || customerorder.getCustomer() == null) {
            return null;
        }
        var customer = customerorder.getCustomer();
        var shortname = customer.getShortname();
        var name = customer.getName();
        if (name == null || name.isBlank()) {
            return shortname;
        }
        if (shortname == null || shortname.isBlank() || derivedFromName(shortname, name)) {
            return name;
        }
        return shortname + " - " + name;
    }

    /**
     * Whether {@code Customer#getShortname()} made this up out of the name — it does that when no
     * short name is stored, returning the name itself or its first nine characters with an ellipsis.
     * Prefixing the name with that would print it twice.
     */
    private static boolean derivedFromName(String shortname, String name) {
        return shortname.equals(name)
            || (shortname.endsWith("...") && name.startsWith(shortname.substring(0, shortname.length() - 3)));
    }

}
