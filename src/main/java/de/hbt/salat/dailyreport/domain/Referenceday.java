package de.hbt.salat.dailyreport.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import de.hbt.salat.common.domain.AuditedEntity;

@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_referenceday_refdate", columnNames = "refdate"))
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Referenceday extends AuditedEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** One row per day (#1208); the database says so as well. */
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
