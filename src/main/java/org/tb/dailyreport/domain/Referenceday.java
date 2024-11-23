package org.tb.dailyreport.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.tb.common.domain.AuditedEntity;

@Getter
@Setter
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Referenceday extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private LocalDate refdate;

    private Boolean workingday;
    /**
     * Day of week
     */
    private String dow;
    private Boolean holiday;
    private String name;

    public String getDow() {
        Map<String, String> weekDaysMap = new HashMap<>();
        weekDaysMap.put("Mon", "main.matrixoverview.weekdays.monday.text");
        weekDaysMap.put("Tue", "main.matrixoverview.weekdays.tuesday.text");
        weekDaysMap.put("Wed", "main.matrixoverview.weekdays.wednesday.text");
        weekDaysMap.put("Thu", "main.matrixoverview.weekdays.thursday.text");
        weekDaysMap.put("Fri", "main.matrixoverview.weekdays.friday.text");
        weekDaysMap.put("Sat", "main.matrixoverview.weekdays.saturday.text");
        weekDaysMap.put("Sun", "main.matrixoverview.weekdays.sunday.text");
        return weekDaysMap.get(dow);
    }

}
