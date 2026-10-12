package de.hbt.salat.dailyreport.domain;

/**
 * @param minutes the booked working time of the month
 * @param surchargeMinutes the time surcharge credit of the month (#1469)
 */
public record MonthlyReportedMinutes(Integer year, Integer month, Long minutes, Long surchargeMinutes) {}
