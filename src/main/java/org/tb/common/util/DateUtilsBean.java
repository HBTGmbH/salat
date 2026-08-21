package org.tb.common.util;

import java.time.YearMonth;
import org.springframework.stereotype.Component;

@Component("dateUtils")
public class DateUtilsBean {

    public String format(YearMonth yearMonth) {
        return DateUtils.format(yearMonth);
    }

    /**
     * The month "today" falls into, taken from the application clock - templates must not fall back
     * to the browser clock for this (see fragments/month-picker).
     */
    public YearMonth getCurrentYearMonth() {
        return YearMonth.from(DateUtils.today());
    }

}
