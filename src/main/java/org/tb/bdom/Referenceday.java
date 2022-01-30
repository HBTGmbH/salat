package org.tb.bdom;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Entity
@EqualsAndHashCode(of = "refdate")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Referenceday implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private long id;

    private java.sql.Date refdate;

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
