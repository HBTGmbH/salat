package org.tb.web.viewhelper;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;

import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.InvoiceSuborderActualHoursVisitor;
import org.tb.bdom.Suborder;
import org.tb.bdom.SuborderVisitor;
import org.tb.bdom.Timereport;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;

public class InvoiceSuborderViewHelper extends Suborder {
	
	private static final long serialVersionUID = 1L;

	private Suborder suborder;

	private TimereportDAO timereportDAO;
	
	private java.sql.Date fromDate;
	
	private java.sql.Date untilDate;

	private List<InvoiceTimereportViewHelper> invoiceTimereportViewHelperList;

	private boolean visible;

	private int layer;

	private boolean invoicebox ;

	public int getLayer() {
		return layer;
	}

	public void setLayer(int layer) {
		this.layer = layer;
	}

	public Suborder getSuborder() {
		return suborder;
	}

	public void setSuborder(Suborder suborder) {
		this.suborder = suborder;
	}

	public boolean isVisible() {
		return visible;
	}

	public String getDuration() {
		InvoiceSuborderActualHoursVisitor visitor = new InvoiceSuborderActualHoursVisitor(timereportDAO, fromDate, untilDate, invoicebox);
		/* start visiting */
		acceptVisitor(visitor);
		/* return result */
		return visitor.getTotalTime();
	}
	
	public long getDurationInMinutes() {
		InvoiceSuborderActualHoursVisitor visitor = new InvoiceSuborderActualHoursVisitor(timereportDAO, fromDate, untilDate, invoicebox);
		/* start visiting */
		acceptVisitor(visitor);
		/* return result */
		return visitor.getTotalMinutes();
	}

	public String getActualhours() {
		return getActualhoursHelper(false);
	}
	
	public String getActualhoursPrint() {
		return getActualhoursHelper(true);
	}
	
	private String getActualhoursHelper(boolean print) {
		long actualhours = getTotalActualminutesHelper(print) / 60;
		long actualminutes = getTotalActualminutesHelper(print) % 60;
		DecimalFormat decimalFormat = new DecimalFormat("00");
		return decimalFormat.format(actualhours) + ":" + decimalFormat.format(actualminutes);
	}
	
	public long getTotalActualminutes() {
		return getTotalActualminutesHelper(false);
	}
	
	public long getTotalActualminutesPrint() {
		return getTotalActualminutesHelper(true);
	}
	
	private long getTotalActualminutesHelper(boolean print) {
		long actualminutes = 0;
		for (InvoiceTimereportViewHelper invoiceTimereportViewHelper: invoiceTimereportViewHelperList) {
			if (!print) {
				actualminutes += invoiceTimereportViewHelper.getDurationminutes();
				actualminutes += invoiceTimereportViewHelper.getDurationhours() * 60;
			} else if (invoiceTimereportViewHelper.isVisible()) {
				actualminutes += invoiceTimereportViewHelper.getDurationminutes();
				actualminutes += invoiceTimereportViewHelper.getDurationhours() * 60;
			}
		}
		return actualminutes;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public List<InvoiceTimereportViewHelper> getInvoiceTimereportViewHelperList() {
		return invoiceTimereportViewHelperList;
	}

	public void setInvoiceTimereportViewHelperList(List<InvoiceTimereportViewHelper> invoiceTimereportViewHelperList) {
		this.invoiceTimereportViewHelperList = invoiceTimereportViewHelperList;
	}

	public InvoiceSuborderViewHelper(Suborder suborder, TimereportDAO timereportDAO, java.sql.Date fromDate, java.sql.Date untilDate, boolean invoicebox) {
		if (suborder == null) {
			throw new IllegalArgumentException("suborder must not be null!");
		}
		this.timereportDAO = timereportDAO;
		this.suborder = suborder;
		this.visible = true;
		this.fromDate = fromDate;
		this.untilDate = untilDate;
		this.invoicebox = invoicebox;
	}

	public void acceptVisitor(SuborderVisitor visitor) {
		suborder.acceptVisitor(visitor);
	}

	public boolean equals(Object obj) {
		return suborder.equals(obj);
	}

	public List<Suborder> getAllChildren() {
		return suborder.getAllChildren();
	}

	public List<Timereport> getAllTimeReportsInvalidForDates(java.sql.Date begin, java.sql.Date end, TimereportDAO timereportDAO) {
		return suborder.getAllTimeReportsInvalidForDates(begin, end, timereportDAO);
	}

	public Boolean getCommentnecessary() {
		return suborder.getCommentnecessary();
	}

	public Date getCreated() {
		return suborder.getCreated();
	}

	public String getCreatedby() {
		return suborder.getCreatedby();
	}

	public String getCurrency() {
		return suborder.getCurrency();
	}

	public boolean getCurrentlyValid() {
		return suborder.getCurrentlyValid();
	}

	public Customerorder getCustomerorder() {
		return suborder.getCustomerorder();
	}

	public Double getDebithours() {
		return suborder.getDebithours();
	}

	public String getDebithoursString() {
		String result = "";
		if (suborder.getDebithours() != null && suborder.getDebithours() != 0.0) {
			DecimalFormat decimalFormat = new DecimalFormat("00");
			String hours = decimalFormat.format(Math.floor(suborder.getDebithours()));
			String minutes = decimalFormat.format(suborder.getDebithours() % 1 * 60);
			result = hours + ":" + minutes;
		}
		return result;
	}

	public Byte getDebithoursunit() {
		return suborder.getDebithoursunit();
	}

	public String getDescription() {
		return suborder.getDescription();
	}

	public String getSuborder_customer() {
		return suborder.getSuborder_customer();
	}

	public List<Employeeorder> getEmployeeorders() {
		return suborder.getEmployeeorders();
	}

	public java.sql.Date getFromDate() {
		return suborder.getFromDate();
	}

	public boolean isHide() {
		return suborder.isHide();
	}

	public Double getHourly_rate() {
		return suborder.getHourly_rate();
	}

	public long getId() {
		return suborder.getId();
	}

	public char getInvoice() {
		return suborder.getInvoice();
	}

	public Character getInvoiceChar() {
		return suborder.getInvoiceChar();
	}

	public String getInvoiceString() {
		return suborder.getInvoiceString();
	}

	public Date getLastupdate() {
		return suborder.getLastupdate();
	}

	public String getLastupdatedby() {
		return suborder.getLastupdatedby();
	}

	public Boolean getNoEmployeeOrderContent() {
		return suborder.getNoEmployeeOrderContent();
	}

	public Suborder getParentorder() {
		return suborder.getParentorder();
	}

	public String getShortdescription() {
		return suborder.getShortdescription();
	}

	public String getSign() {
		return suborder.getSign();
	}

	public String getSignAndDescription() {
		return suborder.getSignAndDescription();
	}

	public Boolean getStandard() {
		return suborder.getStandard();
	}

	public List<Suborder> getSuborders() {
		return suborder.getSuborders();
	}

	public boolean getTimePeriodFitsToUpperElement() {
		return suborder.getTimePeriodFitsToUpperElement();
	}

	public List<Timereport> getTimereports() {
		return suborder.getTimereports();
	}

	public java.sql.Date getUntilDate() {
		return suborder.getUntilDate();
	}

	public Integer getUpdatecounter() {
		return suborder.getUpdatecounter();
	}

	public int hashCode() {
		return suborder.hashCode();
	}

	public void setCommentnecessary(Boolean commentnecessary) {
		suborder.setCommentnecessary(commentnecessary);
	}

	public void setCreated(Date created) {
		suborder.setCreated(created);
	}

	public void setCreatedby(String createdby) {
		suborder.setCreatedby(createdby);
	}

	public void setCurrency(String currency) {
		suborder.setCurrency(currency);
	}

	public void setCustomerorder(Customerorder order) {
		suborder.setCustomerorder(order);
	}

	public void setCustomerOrderForAllDescendants(Customerorder customerOrder,
			SuborderDAO suborderDAO, Employee loginEmployee,
			Suborder rootSuborder) {
		suborder.setCustomerOrderForAllDescendants(customerOrder, suborderDAO,
				loginEmployee, rootSuborder);
	}

	public void setDebithours(Double debithours) {
		suborder.setDebithours(debithours);
	}

	public void setDebithoursunit(Byte debithoursunit) {
		suborder.setDebithoursunit(debithoursunit);
	}

	public void setDescription(String description) {
		suborder.setDescription(description);
	}

	public void setSuborder_customer(String suborder_customer) {
		suborder.setSuborder_customer(suborder_customer);
	}

	public void setEmployeeorders(List<Employeeorder> employeeorders) {
		suborder.setEmployeeorders(employeeorders);
	}

	public void setFromDate(java.sql.Date fromDate) {
		suborder.setFromDate(fromDate);
	}

	public void setHide(Boolean hide) {
		suborder.setHide(hide);
	}

	public void setHourly_rate(Double hourly_rate) {
		suborder.setHourly_rate(hourly_rate);
	}

	public void setId(long id) {
		suborder.setId(id);
	}

	public void setInvoice(char invoice) {
		suborder.setInvoice(invoice);
	}

	public void setLastupdate(Date lastupdate) {
		suborder.setLastupdate(lastupdate);
	}

	public void setLastupdatedby(String lastupdatedby) {
		suborder.setLastupdatedby(lastupdatedby);
	}

	public void setNoEmployeeOrderContent(Boolean noEmployeeOrderContent) {
		suborder.setNoEmployeeOrderContent(noEmployeeOrderContent);
	}

	public void setParentorder(Suborder parentorder) {
		suborder.setParentorder(parentorder);
	}

	public void setShortdescription(String shortdescription) {
		suborder.setShortdescription(shortdescription);
	}

	public void setSign(String sign) {
		suborder.setSign(sign);
	}

	public void setStandard(Boolean standard) {
		suborder.setStandard(standard);
	}

	public void setSuborders(List<Suborder> suborders) {
		suborder.setSuborders(suborders);
	}

	public void setTimereports(List<Timereport> timereports) {
		suborder.setTimereports(timereports);
	}

	public void setUntilDate(java.sql.Date untilDate) {
		suborder.setUntilDate(untilDate);
	}

	public void setUpdatecounter(Integer updatecounter) {
		suborder.setUpdatecounter(updatecounter);
	}

	public String toString() {
		return suborder.toString();
	}

	public boolean validityPeriodFitsToCustomerOrder() {
		return suborder.validityPeriodFitsToCustomerOrder();
	}
}
