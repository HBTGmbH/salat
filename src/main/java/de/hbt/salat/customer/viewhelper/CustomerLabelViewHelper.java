package de.hbt.salat.customer.viewhelper;

import org.springframework.stereotype.Component;
import de.hbt.salat.customer.domain.Customer;

/**
 * How a customer is named in a select (#1266, → ADR-0017): {@code Kurzname - Name}, the name once
 * when there is no short name of its own.
 *
 * <p>The same label stands underneath every order and suborder as {@code data-subtext}
 * ({@code CustomerorderViewHelper}), so a customer reads the same wherever it is picked or shown as
 * context, and is findable by either name.
 *
 * <p>Used from templates as {@code ${@customerLabelViewHelper.label(c)}}; the hidden marker is
 * appended there as for every other select ({@code HiddenMarkerViewHelper}).
 */
@Component
public class CustomerLabelViewHelper {

    public String label(Customer customer) {
        return customer == null ? null : of(customer.getShortname(), customer.getName());
    }

    /** For a record that carries the two names rather than the customer. */
    public String label(String shortname, String name) {
        return of(shortname, name);
    }

    /**
     * For code that has the two names but not the customer. {@code shortname} is what
     * {@link Customer#getShortname()} answers, including the one it makes up from the name.
     */
    public static String of(String shortname, String name) {
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
