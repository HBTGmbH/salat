package org.tb.web.viewhelper;

import java.util.Date;

import org.apache.commons.lang.StringEscapeUtils;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Referenceday;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;

public class InvoiceTimereportViewHelper {
	
	private Timereport timereport;
	private boolean visible;

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
	
	public InvoiceTimereportViewHelper(Timereport timereport) {
		this.timereport = timereport;
		this.visible = true;
	}

	public Date getAccepted() {
		return timereport.getAccepted();
	}

	public String getAcceptedby() {
		return timereport.getAcceptedby();
	}

	public Double getCosts() {
		return timereport.getCosts();
	}

	public Date getCreated() {
		return timereport.getCreated();
	}

	public String getCreatedby() {
		return timereport.getCreatedby();
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

	public Date getLastupdate() {
		return timereport.getLastupdate();
	}

	public String getLastupdatedby() {
		return timereport.getLastupdatedby();
	}

	public Referenceday getReferenceday() {
		return timereport.getReferenceday();
	}

	public Date getReleased() {
		return timereport.getReleased();
	}

	public String getReleasedby() {
		return timereport.getReleasedby();
	}

	public int getSequencenumber() {
		return timereport.getSequencenumber();
	}

	public String getSortofreport() {
		return timereport.getSortofreport();
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
		if(timereport.getTaskdescription() != null) {
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

	public int hashCode() {
		return timereport.hashCode();
	}

	public void setAccepted(Date accepted) {
		timereport.setAccepted(accepted);
	}

	public void setAcceptedby(String acceptedby) {
		timereport.setAcceptedby(acceptedby);
	}

	public void setCosts(Double costs) {
		timereport.setCosts(costs);
	}

	public void setCreated(Date created) {
		timereport.setCreated(created);
	}

	public void setCreatedby(String createdby) {
		timereport.setCreatedby(createdby);
	}

	public void setDurationhours(Integer durationhours) {
		timereport.setDurationhours(durationhours);
	}

	public void setDurationminutes(Integer durationminutes) {
		timereport.setDurationminutes(durationminutes);
	}

	public void setEmployeecontract(Employeecontract employeecontract) {
		timereport.setEmployeecontract(employeecontract);
	}

	public void setEmployeeorder(Employeeorder employeeorder) {
		timereport.setEmployeeorder(employeeorder);
	}

	public void setId(long id) {
		timereport.setId(id);
	}

	public void setLastupdate(Date lastupdate) {
		timereport.setLastupdate(lastupdate);
	}

	public void setLastupdatedby(String lastupdatedby) {
		timereport.setLastupdatedby(lastupdatedby);
	}

	public void setReferenceday(Referenceday referenceday) {
		timereport.setReferenceday(referenceday);
	}

	public void setReleased(Date released) {
		timereport.setReleased(released);
	}

	public void setReleasedby(String releasedby) {
		timereport.setReleasedby(releasedby);
	}

	public void setSequencenumber(int sequencenumber) {
		timereport.setSequencenumber(sequencenumber);
	}

	public void setSortofreport(String sortofreport) {
		timereport.setSortofreport(sortofreport);
	}

	public void setStatus(String status) {
		timereport.setStatus(status);
	}

	public void setSuborder(Suborder order) {
		timereport.setSuborder(order);
	}

	public void setTaskdescription(String taskdescription) {
		timereport.setTaskdescription(taskdescription);
	}

	public void setUpdatecounter(Integer updatecounter) {
		timereport.setUpdatecounter(updatecounter);
	}

	public String toString() {
		return timereport.toString();
	}
	
	
}
