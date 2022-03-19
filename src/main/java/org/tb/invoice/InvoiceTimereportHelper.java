package org.tb.invoice;

import static org.tb.common.util.TimeFormatUtils.decimalFormatHoursAndMinutes;
import static org.tb.common.util.TimeFormatUtils.timeFormatHoursAndMinutes;

import java.time.LocalDateTime;
import org.apache.commons.lang.StringEscapeUtils;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;

public class InvoiceTimereportHelper {

    private Timereport timereport;
    private boolean visible;

    public InvoiceTimereportHelper(Timereport timereport) {
        this.timereport = timereport;
        this.visible = true;
    }

    public Timereport getTimereport() {
        return timereport;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public LocalDateTime getAccepted() {
        return timereport.getAccepted();
    }

    public String getAcceptedby() {
        return timereport.getAcceptedby();
    }

    public LocalDateTime getCreated() {
        return timereport.getCreated();
    }

    public String getCreatedby() {
        return timereport.getCreatedby();
    }

    public String getDurationString() {
        return timeFormatHoursAndMinutes(timereport.getDurationhours(), timereport.getDurationminutes());
    }

    public String getHoursString() {
        return decimalFormatHoursAndMinutes(timereport.getDurationhours(), timereport.getDurationminutes());
    }

    public Integer getDurationhours() {
        return timereport.getDurationhours();
    }

    public Integer getDurationminutes() {
        return timereport.getDurationminutes();
    }

    public Employeecontract getEmployeecontract() {
        return timereport.getEmployeecontract();
    }

    public Employeeorder getEmployeeorder() {
        return timereport.getEmployeeorder();
    }

    public boolean getFitsToContract() {
        return timereport.getFitsToContract();
    }

    public long getId() {
        return timereport.getId();
    }

    public LocalDateTime getLastupdate() {
        return timereport.getLastupdate();
    }

    public String getLastupdatedby() {
        return timereport.getLastupdatedby();
    }

    public Referenceday getReferenceday() {
        return timereport.getReferenceday();
    }

    public LocalDateTime getReleased() {
        return timereport.getReleased();
    }

    public String getReleasedby() {
        return timereport.getReleasedby();
    }

    public int getSequencenumber() {
        return timereport.getSequencenumber();
    }

    public String getStatus() {
        return timereport.getStatus();
    }

    public Suborder getSuborder() {
        return timereport.getSuborder();
    }

    public String getTaskdescription() {
        return timereport.getTaskdescription();
    }

    public String getTaskdescriptionHtml() {
        if (timereport.getTaskdescription() != null) {
            return StringEscapeUtils.escapeHtml(timereport.getTaskdescription()).replaceAll("\n", "<br>");
        } else {
            return null;
        }
    }

    public String getTimeReportAsString() {
        return timereport.getTimeReportAsString();
    }

    public Timereport getTwin() {
        return timereport.getTwin();
    }

    public Integer getUpdatecounter() {
        return timereport.getUpdatecounter();
    }

}
