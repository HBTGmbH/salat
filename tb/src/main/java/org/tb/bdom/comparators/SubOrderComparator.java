package org.tb.bdom.comparators;

import java.util.Comparator;

import org.tb.bdom.Suborder;

public class SubOrderComparator implements Comparator<Suborder> {

	/**
	 * Compares {@link Suborder}s by {@link Suborder#getSign()} (1st criteria) and {@link Suborder#getDescription()} (2nd criteria).
	 * 
	 * @param so1 first {@link Suborder}
	 * @param so2 second {@link Suborder}
	 * @return Returns -1, 0, 1 if the first {@link Suborder} is less, equal, greater than the second one.
	 */
	public int compare(Suborder so1, Suborder so2) {
		if(so1.getSign() != null && so2.getSign() != null) {
			int comp = String.CASE_INSENSITIVE_ORDER.compare(so1.getSign(), so2.getSign());
			if(comp != 0) return comp;
		}
		
		// if both have same signs check description
		if (so1.getDescription() != null && so2.getDescription() != null) {
			int comp = so1.getDescription().compareTo(so2.getDescription());
			if(comp != 0) return comp;
		}
		
		// oldest suborders at the end
		if (so1.getUntilDate() != null && so2.getUntilDate() != null
				&& so1.getUntilDate().compareTo(so2.getUntilDate()) < 0) {
			return 1;
		} else if (so1.getUntilDate() != null && so2.getUntilDate() != null
				&& so1.getUntilDate().compareTo(so2.getUntilDate()) > 0) {
			return -1;
		}

		// both look the same
		return Long.valueOf(so1.getId()).compareTo(Long.valueOf(so2.getId()));
	}

}
