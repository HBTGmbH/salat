package org.tb.bdom.comparators;

import java.util.Comparator;

import org.tb.bdom.Customerorder;

public class CustomerOrderComparator implements Comparator<Customerorder> {

	
	/**
	 * Compares  {@link Customerorder}s by {@link Customerorder#getSign()}, {@link Customerorder#getDescription()}, 
	 * {@link Customerorder#getFromDate()} and {@link Customerorder#getUntilDate()}.
	 * 
	 * @param co1 {@link Customerorder} No1
	 * @param co2 {@link Customerorder} No2
	 * @return Returns -1, 0, 1 if the first {@link Customerorder} is less, equal, greater than the second one.
	 */
	public int compare(Customerorder co1, Customerorder co2) {
		if (co1.getSign().compareTo(co2.getSign()) < 0) {
			return -1;
		} else if (co1.getSign().compareTo(co2.getSign()) > 0) {
			return 1;
		} else {
			if (co1.getDescription().compareTo(co2.getDescription()) < 0) {
				return -1;
			} else if (co1.getDescription().compareTo(co2.getDescription()) > 0) {
				return 1;
			} else {
				if (co1.getFromDate().compareTo(co2.getFromDate()) < 0) {
					return -1;
				} else if (co1.getFromDate().compareTo(co2.getFromDate()) > 0) {
					return 1;
				} else {
					if (co1.getUntilDate().compareTo(co2.getUntilDate()) < 0) {
						return -1;
					} else if (co1.getUntilDate().compareTo(co2.getUntilDate()) > 0) {
						return 1;
					}
				}
			}
		}
		return 0;
	}

}
