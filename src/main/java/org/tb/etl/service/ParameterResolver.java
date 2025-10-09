package org.tb.etl.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.tb.common.LocalDateRange;

/**
 * Ersetzt Platzhalter wie ${BOM}, ${EOM} usw. im SQL-Text.
 */
@Component
public class ParameterResolver {

  public String resolve(String sql, LocalDateRange referencePeriod) {
    LocalDate refDate = referencePeriod.getFrom();
    Map<String, String> params = new HashMap<>();
    params.put("TODAY", refDate.toString());
    params.put("YESTERDAY", refDate.toString());
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
    params.put("FROM", String.valueOf(referencePeriod.getFrom().toString()));
    params.put("UNTIL", String.valueOf(referencePeriod.getUntil().toString()));

    String result = sql;
    for (Map.Entry<String, String> entry : params.entrySet()) {
      result = result.replace("${" + entry.getKey() + "}", entry.getValue());
    }
    return result;
  }
}
