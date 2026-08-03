package org.tb.common.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component("dateUtils")
public class DateUtilsBean {

    public String format(YearMonth yearMonth) {
        return DateUtils.format(yearMonth);
    }

    public String format(LocalDate date) {
        return DateUtils.formatDisplay(date);
    }

    public String formatWithDow(LocalDate date, Locale locale) {
        return DateUtils.formatDisplayWithDow(date, locale);
    }

    public String formatYearMonth(LocalDate date, Locale locale) {
        return DateUtils.formatYearMonth(date, locale);
    }

}
