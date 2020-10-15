package org.tb.bdom.comparators;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.tb.bdom.Customerorder;

import java.util.Comparator;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public class CustomerOrderComparator implements Comparator<Customerorder> {

    public static final Comparator<Customerorder> INSTANCE = new CustomerOrderComparator();

    /**
     * Compares  {@link Customerorder}s by {@link Customerorder#getSign()}, {@link Customerorder#getDescription()} and
     * {@link Customerorder#getFromDate()}.
     *
     * @param co1 {@link Customerorder} No1
     * @param co2 {@link Customerorder} No2
     * @return Returns -1, 0, 1 if the first {@link Customerorder} is less, equal, greater than the second one.
     */
    public int compare(Customerorder co1, Customerorder co2) {
        // check the signs
        if (co1.getSign() != null && co2.getSign() != null
                && co1.getSign().compareTo(co2.getSign()) < 0) {
            return -1;
        } else if (co1.getSign() != null && co2.getSign() != null
                && co1.getSign().compareTo(co2.getSign()) > 0) {
            return 1;
        }
        // if both have same signs check description
        if (co1.getDescription() != null && co2.getDescription() != null
                && co1.getDescription().compareTo(co2.getDescription()) < 0) {
            return -1;
        } else if (co1.getDescription() != null && co2.getDescription() != null
                && co1.getDescription().compareTo(co2.getDescription()) > 0) {
            return 1;
        }
        // if both have same descriptions check fromdate
        if (co1.getFromDate() != null && co2.getFromDate() != null
                && co1.getFromDate().compareTo(co2.getFromDate()) < 0) {
            return -1;
        } else if (co1.getFromDate() != null && co2.getFromDate() != null
                && co1.getFromDate().compareTo(co2.getFromDate()) > 0) {
            return 1;
        }
        return 0;
    }

}
