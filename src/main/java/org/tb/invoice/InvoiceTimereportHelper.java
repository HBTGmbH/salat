package org.tb.invoice;

import java.time.LocalDate;
import org.apache.commons.lang.StringEscapeUtils;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.TimereportDTO;

public class InvoiceTimereportHelper {

    private TimereportDTO timereport;
    private boolean visible;

    public InvoiceTimereportHelper(TimereportDTO timereport) {
        this.timereport = timereport;
        this.visible = true;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public String getDurationString() {
        return DurationUtils.format(timereport.getDuration());
    }

    public String getHoursString() {
        return DurationUtils.decimalFormat(timereport.getDuration());
    }

    public String getTaskdescriptionHtml() {
        if (timereport.getTaskdescription() != null) {
            return StringEscapeUtils.escapeHtml(timereport.getTaskdescription()).replaceAll("\n", "<br>");
        } else {
            return null;
        }
    }

    public Long getId() {
        return timereport.getId();
    }

    public Long getDurationhours() {
        return timereport.getDuration().toHours();
    }

    public Integer getDurationminutes() {
        return timereport.getDuration().toMinutesPart();
    }

    public LocalDate getReferenceday() {
        return timereport.getReferenceday();
    }

    public Long getEmployeecontract() {
        return timereport.getEmployeecontractId();
    }

    public String getTaskdescription() {
        return timereport.getTaskdescription();
    }

    public String getEmployeeSign() {
        return timereport.getEmployeeSign();
    }

    public String getEmployeeName() {
        return timereport.getEmployeeName();
    }

}
