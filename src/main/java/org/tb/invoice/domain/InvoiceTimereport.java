package org.tb.invoice.domain;

import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import lombok.Data;
import org.tb.dailyreport.domain.TimereportDTO;

@Data
public class InvoiceTimereport implements Serializable {

    private final long id;
    private final LocalDate referenceDay;
    private final String taskDescription;
    private final String employeeName;
    private final Duration duration;
    private boolean visible;

    public InvoiceTimereport(TimereportDTO timereport) {
        this.id = timereport.getId();
        this.referenceDay = timereport.getReferenceday();
        this.taskDescription = timereport.getTaskdescription();
        this.employeeName = timereport.getEmployeeName();
        this.duration = timereport.getDuration();
        this.visible = true;
    }

    public BigDecimal getHours() {
        return BigDecimal
            .valueOf(duration.toMinutes())
            .divide(BigDecimal.valueOf(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
    }

}
