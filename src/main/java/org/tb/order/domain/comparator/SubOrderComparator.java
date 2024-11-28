package org.tb.order.domain.comparator;

import static lombok.AccessLevel.PRIVATE;

import java.util.Comparator;
import lombok.NoArgsConstructor;
import org.tb.order.domain.Suborder;

@NoArgsConstructor(access = PRIVATE)
public class SubOrderComparator implements Comparator<Suborder> {

    public static final Comparator<Suborder> INSTANCE = new SubOrderComparator();

    /**
     * Compares {@link Suborder}s by {@link Suborder#getCompleteOrderSign()} (1st criteria) and {@link Suborder#getDescription()} (2nd criteria).
     *
     * @param so1 first {@link Suborder}
     * @param so2 second {@link Suborder}
     * @return Returns -1, 0, 1 if the first {@link Suborder} is less, equal, greater than the second one.
     */
    public int compare(Suborder so1, Suborder so2) {
        if (so1.getCompleteOrderSign() != null && so2.getCompleteOrderSign() != null) {
            int comp = String.CASE_INSENSITIVE_ORDER.compare(so1.getCompleteOrderSign(), so2.getCompleteOrderSign());
            if (comp != 0) return comp;
        }

        // if both have same signs check description
        if (so1.getDescription() != null && so2.getDescription() != null) {
            int comp = String.CASE_INSENSITIVE_ORDER.compare(so1.getDescription(), so2.getDescription());
            if (comp != 0) return comp;
        }

        // oldest suborders at the end
        if (so1.getUntilDate() != null && so2.getUntilDate() != null) {
            int comp = so1.getUntilDate().compareTo(so2.getUntilDate());
            if (comp != 0) return comp;
        }

        // both look the same
        return so1.getId().compareTo(so2.getId());
    }

}
