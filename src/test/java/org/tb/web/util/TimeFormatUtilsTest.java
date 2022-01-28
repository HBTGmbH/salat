package org.tb.web.util;

import static org.tb.web.util.TimeFormatUtils.*;

import junit.framework.TestCase;

public class TimeFormatUtilsTest extends TestCase {

  public void testDecimalFormatHours() {
    assertEquals("8,50", decimalFormatHours(8.5));
    assertEquals("2,44", decimalFormatHours(2.44));
    assertEquals("2,50", decimalFormatHours(2.501));
    assertEquals("0,44", decimalFormatHours(0.44));
    assertEquals("0,40", decimalFormatHours(0.4));
  }

  public void testDecimalFormatHoursAndMinutes() {
    assertEquals("8,50", decimalFormatHoursAndMinutes(8, 30));
    assertEquals("0,50", decimalFormatHoursAndMinutes(0, 30));
    assertEquals("2,85", decimalFormatHoursAndMinutes(2, 51));
    assertEquals("3,87", decimalFormatHoursAndMinutes(3, 52));
  }

  public void testTimeFormatHoursAndMinutes() {
    assertEquals("8:30", timeFormatHoursAndMinutes(8, 30));
    assertEquals("-8:30", timeFormatHoursAndMinutes(-8, -30));
    assertEquals("0:03", timeFormatHoursAndMinutes(0, 3));
  }

  public void testTimeFormatMinutes() {
    assertEquals("4:30", timeFormatMinutes(270));
    assertEquals("-4:30", timeFormatMinutes(-270));
    assertEquals("0:03", timeFormatMinutes(3));
  }

  public void testDecimalFormatMinutes() {
    assertEquals("4,50", decimalFormatMinutes(270));
    assertEquals("4,53", decimalFormatMinutes(272));
    assertEquals("0,05", decimalFormatMinutes(3));
  }

  public void testTimeFormatHours() {
    assertEquals("3:00", timeFormatHours(3));
    assertEquals("3:30", timeFormatHours(3.5));
    assertEquals("3:45", timeFormatHours(3.75));
    assertEquals("2:46", timeFormatHours(2.7641));
  }
}
