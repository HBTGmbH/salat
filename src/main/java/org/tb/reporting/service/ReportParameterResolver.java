package org.tb.reporting.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves placeholders like ${BOM}, ${EOM}, etc. for Reporting only.
 *
 * Unlike the ETL ParameterResolver, this resolver:
 * - uses only "today" as the reference date (passed in)
 * - intentionally does NOT resolve ${FROM} or ${UNTIL}
 */
@Component
public class ReportParameterResolver {

  public String resolve(String sql, LocalDate today) {
    LocalDate refDate = today;
    Map<String, String> params = new HashMap<>();
    params.put("TODAY", refDate.toString());
    params.put("YESTERDAY", refDate.minusDays(1).toString());
    params.put("BOM", refDate.withDayOfMonth(1).toString());
    params.put("EOM", refDate.with(TemporalAdjusters.lastDayOfMonth()).toString());
    params.put("BOQ", refDate.with(refDate.getMonth().firstMonthOfQuarter()).withDayOfMonth(1).toString());
    params.put("EOQ",
        refDate.with(refDate.getMonth().firstMonthOfQuarter()).plusMonths(2).with(TemporalAdjusters.lastDayOfMonth())
            .toString());
    params.put("BOY", refDate.withDayOfYear(1).toString());
    params.put("EOY", refDate.with(TemporalAdjusters.lastDayOfYear()).toString());
    params.put("BOW", refDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString());
    params.put("EOW", refDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toString());
    params.put("WEEKNUM", String.valueOf(refDate.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())));
    params.put("MONTH", String.valueOf(refDate.getMonthValue()));
    params.put("QUARTER", String.valueOf((refDate.getMonthValue() - 1) / 3 + 1));
    params.put("YEAR", String.valueOf(refDate.getYear()));
    // Intentionally skip FROM and UNTIL

    String result = sql;
    for (Map.Entry<String, String> entry : params.entrySet()) {
      result = result.replace("${" + entry.getKey() + "}", entry.getValue());
    }
    return result;
  }
}
