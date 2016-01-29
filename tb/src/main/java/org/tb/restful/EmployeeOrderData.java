package org.tb.restful;

public class EmployeeOrderData {
	private SuborderData suborder;
	private long employeeorderId;
	public SuborderData getSuborder() {
		return suborder;
	}
	public void setSuborder(SuborderData suborder) {
		this.suborder = suborder;
	}
	public long getEmployeeorderId() {
		return employeeorderId;
	}
	public void setEmployeeorderId(long employeeorderId) {
		this.employeeorderId = employeeorderId;
	}
}
