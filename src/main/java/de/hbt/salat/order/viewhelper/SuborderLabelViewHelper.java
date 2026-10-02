package de.hbt.salat.order.viewhelper;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * Labels for a suborder in dropdowns (#1266, → ADR-0017): {@code Auftrag/Unterauftrag -
 * Kurzbeschreibung}, and underneath {@code Auftrag-Kurzbeschreibung · Kunde}.
 *
 * <p>The complete sign already starts with the order's sign, so the second line names what the sign
 * does not: what the order is and whose it is. Both are searched, so a suborder is findable by its
 * order's description and by its customer, not only by its own sign.
 *
 * <p>Used from templates as {@code ${@suborderLabelViewHelper.label(so)}} and
 * {@code ${@suborderLabelViewHelper.subtext(so)}}; the hidden marker is appended there as for every
 * other select ({@code HiddenMarkerViewHelper}). Code that works on summaries rather than entities
 * calls {@link #of(String, String)} and {@link #subtextOf(String, String)}.
 */
@Component
public class SuborderLabelViewHelper {

    public String label(Suborder suborder) {
        return suborder == null ? null : of(suborder.getCompleteOrderSign(), suborder.getShortdescription());
    }

    public String subtext(Suborder suborder) {
        return suborder == null ? null : subtextOfOrder(suborder.getCustomerorder());
    }

    /** The second line for the suborders of this order. */
    public static String subtextOfOrder(Customerorder customerorder) {
        return customerorder == null ? null
            : subtextOf(customerorder.getShortdescription(), CustomerorderViewHelper.customerOf(customerorder));
    }

    public static String of(String completeOrderSign, String shortdescription) {
        return CustomerorderViewHelper.of(completeOrderSign, shortdescription);
    }

    /** {@code null} when neither part is there — the attribute is then left out entirely. */
    public static String subtextOf(String orderShortdescription, String customerLabel) {
        var subtext = Stream.of(orderShortdescription, customerLabel)
            .filter(Objects::nonNull)
            .filter(part -> !part.isBlank())
            .collect(Collectors.joining(" · "));
        return subtext.isEmpty() ? null : subtext;
    }

}
