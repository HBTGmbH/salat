package org.tb.util;

import junit.framework.TestCase;

public class DateUtilsTest extends TestCase {

	/*
	 * Test method for 'org.tb.util.DateUtils.getLastDayOfMonth(String, String)'
	 */
	public final void testGetLastDayOfMonth() {
		TestCase.assertEquals(28, DateUtils.getLastDayOfMonth("2006", "02"));
	}

}
