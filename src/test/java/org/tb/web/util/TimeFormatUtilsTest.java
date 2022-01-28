package org.tb.web.util;

import junit.framework.TestCase;

public class TimeFormatUtilsTest extends TestCase {

  public void testDecimalFormatHours() {
    assertEquals("8,50", TimeFormatUtils.decimalFormatHours(8.5));
    assertEquals("2,44", TimeFormatUtils.decimalFormatHours(2.44));
    assertEquals("2,50", TimeFormatUtils.decimalFormatHours(2.501));
    assertEquals("0,44", TimeFormatUtils.decimalFormatHours(0.44));
    assertEquals("0,40", TimeFormatUtils.decimalFormatHours(0.4));
  }

  public void testDecimalFormatHoursAndMinutes() {
    assertEquals("8,50", TimeFormatUtils.decimalFormatHoursAndMinutes(8, 30));
    assertEquals("0,50", TimeFormatUtils.decimalFormatHoursAndMinutes(0, 30));
    assertEquals("2,85", TimeFormatUtils.decimalFormatHoursAndMinutes(2, 51));
    assertEquals("3,87", TimeFormatUtils.decimalFormatHoursAndMinutes(3, 52));
  }

  public void testTimeFormatHoursAndMinutes() {
    assertEquals("8:30", TimeFormatUtils.timeFormatHoursAndMinutes(8, 30));
    assertEquals("0:03", TimeFormatUtils.timeFormatHoursAndMinutes(0, 3));
  }

  public void testTimeFormatMinutes() {
    assertEquals("4:30", TimeFormatUtils.timeFormatMinutes(270));
    assertEquals("0:03", TimeFormatUtils.timeFormatMinutes(3));
  }

  public void testDecimalFormatMinutes() {
    assertEquals("4,50", TimeFormatUtils.decimalFormatMinutes(270));
    assertEquals("4,53", TimeFormatUtils.decimalFormatMinutes(272));
    assertEquals("0,05", TimeFormatUtils.decimalFormatMinutes(3));
  }

  public void testTimeFormatHours() {
    assertEquals("3:00", TimeFormatUtils.timeFormatHours(3));
    assertEquals("3:30", TimeFormatUtils.timeFormatHours(3.5));
    assertEquals("3:45", TimeFormatUtils.timeFormatHours(3.75));
    assertEquals("2:46", TimeFormatUtils.timeFormatHours(2.7641));
  }
}
