package org.tb.web.viewhelper;

import org.apache.commons.lang.StringEscapeUtils;
import org.tb.bdom.*;

import java.util.Date;

public class InvoiceTimereportViewHelper {

    private Timereport timereport;
    private boolean visible;

    public InvoiceTimereportViewHelper(Timereport timereport) {
        this.timereport = timereport;
        this.visible = true;
    }

    public Timereport getTimereport() {
        return timereport;
    }

    public void setTimereport(Timereport timereport) {
        this.timereport = timereport;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public Date getAccepted() {
        return timereport.getAccepted();
    }

    public void setAccepted(Date accepted) {
        timereport.setAccepted(accepted);
    }

    public String getAcceptedby() {
        return timereport.getAcceptedby();
    }

    public void setAcceptedby(String acceptedby) {
        timereport.setAcceptedby(acceptedby);
    }

    public Double getCosts() {
        return timereport.getCosts();
    }

    public void setCosts(Double costs) {
        timereport.setCosts(costs);
    }

    public Date getCreated() {
        return timereport.getCreated();
    }

    public void setCreated(Date created) {
        timereport.setCreated(created);
    }

    public String getCreatedby() {
        return timereport.getCreatedby();
    }

    public void setCreatedby(String createdby) {
        timereport.setCreatedby(createdby);
    }

    public Integer getDurationhours() {
        return timereport.getDurationhours();
    }

    public void setDurationhours(Integer durationhours) {
        timereport.setDurationhours(durationhours);
    }

    public Integer getDurationminutes() {
        return timereport.getDurationminutes();
    }

    public void setDurationminutes(Integer durationminutes) {
        timereport.setDurationminutes(durationminutes);
    }

    public Employeecontract getEmployeecontract() {
        return timereport.getEmployeecontract();
    }

    public void setEmployeecontract(Employeecontract employeecontract) {
        timereport.setEmployeecontract(employeecontract);
    }

    public Employeeorder getEmployeeorder() {
        return timereport.getEmployeeorder();
    }

    public void setEmployeeorder(Employeeorder employeeorder) {
        timereport.setEmployeeorder(employeeorder);
    }

    public boolean getFitsToContract() {
        return timereport.getFitsToContract();
    }

    public long getId() {
        return timereport.getId();
    }

    public void setId(long id) {
        timereport.setId(id);
    }

    public Date getLastupdate() {
        return timereport.getLastupdate();
    }

    public void setLastupdate(Date lastupdate) {
        timereport.setLastupdate(lastupdate);
    }

    public String getLastupdatedby() {
        return timereport.getLastupdatedby();
    }

    public void setLastupdatedby(String lastupdatedby) {
        timereport.setLastupdatedby(lastupdatedby);
    }

    public Referenceday getReferenceday() {
        return timereport.getReferenceday();
    }

    public void setReferenceday(Referenceday referenceday) {
        timereport.setReferenceday(referenceday);
    }

    public Date getReleased() {
        return timereport.getReleased();
    }

    public void setReleased(Date released) {
        timereport.setReleased(released);
    }

    public String getReleasedby() {
        return timereport.getReleasedby();
    }

    public void setReleasedby(String releasedby) {
        timereport.setReleasedby(releasedby);
    }

    public int getSequencenumber() {
        return timereport.getSequencenumber();
    }

    public void setSequencenumber(int sequencenumber) {
        timereport.setSequencenumber(sequencenumber);
    }

    public String getSortofreport() {
        return timereport.getSortofreport();
    }

    public void setSortofreport(String sortofreport) {
        timereport.setSortofreport(sortofreport);
    }

    public String getStatus() {
        return timereport.getStatus();
    }

    public void setStatus(String status) {
        timereport.setStatus(status);
    }

    public Suborder getSuborder() {
        return timereport.getSuborder();
    }

    public void setSuborder(Suborder order) {
        timereport.setSuborder(order);
    }

    public String getTaskdescription() {
        return timereport.getTaskdescription();
    }

    public void setTaskdescription(String taskdescription) {
        timereport.setTaskdescription(taskdescription);
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

    public void setUpdatecounter(Integer updatecounter) {
        timereport.setUpdatecounter(updatecounter);
    }

    public int hashCode() {
        return timereport.hashCode();
    }

    public String toString() {
        return timereport.toString();
    }


}
