package org.tb.common.util;

import java.time.YearMonth;
import org.springframework.stereotype.Component;

@Component("dateUtils")
public class DateUtilsBean {

    public String format(YearMonth yearMonth) {
        return DateUtils.format(yearMonth);
    }

}
