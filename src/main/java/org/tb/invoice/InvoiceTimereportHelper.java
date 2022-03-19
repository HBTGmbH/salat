package org.tb.invoice;

import static org.tb.common.util.TimeFormatUtils.decimalFormatHoursAndMinutes;
import static org.tb.common.util.TimeFormatUtils.timeFormatHoursAndMinutes;

import org.apache.commons.lang.StringEscapeUtils;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employeecontract;

public class InvoiceTimereportHelper {

    private Timereport timereport;
    private boolean visible;

    public InvoiceTimereportHelper(Timereport timereport) {
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
        return timeFormatHoursAndMinutes(timereport.getDurationhours(), timereport.getDurationminutes());
    }

    public String getHoursString() {
        return decimalFormatHoursAndMinutes(timereport.getDurationhours(), timereport.getDurationminutes());
    }

    public String getTaskdescriptionHtml() {
        if (timereport.getTaskdescription() != null) {
            return StringEscapeUtils.escapeHtml(timereport.getTaskdescription()).replaceAll("\n", "<br>");
        } else {
            return null;
        }
    }

    public long getId() {
        return timereport.getId();
    }

    public Integer getDurationhours() {
        return timereport.getDurationhours();
    }

    public Integer getDurationminutes() {
        return timereport.getDurationminutes();
    }

    public Referenceday getReferenceday() {
        return timereport.getReferenceday();
    }

    public Employeecontract getEmployeecontract() {
        return timereport.getEmployeecontract();
    }

    public String getTaskdescription() {
        return timereport.getTaskdescription();
    }

}
