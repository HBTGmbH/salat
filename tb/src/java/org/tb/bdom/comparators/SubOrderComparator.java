package org.tb.bdom.comparators;

import java.util.Comparator;

import org.tb.bdom.Suborder;

public class SubOrderComparator implements Comparator<Suborder> {

	/**
	 * Compares {@link Suborder}s by {@link Suborder#getSign()} and {@link Suborder#getDescription()}.
	 * 
	 * @param so1
	 * @param so2
	 * @return Returns -1, 0, 1 if the first {@link Suborder} is less, equal, greater than the second one.
	 */
	public int compare(Suborder so1, Suborder so2) {
		if (so1.getSign().compareTo(so2.getSign()) < 0) {
			return -1;
		} else if (so1.getSign().compareTo(so2.getSign()) > 0) {
			return 1;
		} else {
			if (so1.getDescription().compareTo(so2.getDescription()) < 0) {
				return -1;
			} else if (so1.getDescription().compareTo(so2.getDescription()) > 0) {
				return 1;
			}
		}
		return 0;
	}

}
