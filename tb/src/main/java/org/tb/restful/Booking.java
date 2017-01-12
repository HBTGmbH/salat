package org.tb.restful;

public class Booking {

	private long employeeorderId;
	private String orderLabel;
	private String suborderLabel;
	private int hours;
	private int minutes;
	private String comment;
	private boolean isTraining;
	private String suborderSign;
	private String orderSign;
	private String date;
	private double costs;

	public long getEmployeeorderId() {
		return employeeorderId;
	}
	public void setEmployeeorderId(long employeeorderId) {
		this.employeeorderId = employeeorderId;
	}
	public String getOrderLabel() {
		return orderLabel;
	}
	public void setOrderLabel(String orderLabel) {
		this.orderLabel = orderLabel;
	}
	public String getSuborderLabel() {
		return suborderLabel;
	}
	public void setSuborderLabel(String suborderLabel) {
		this.suborderLabel = suborderLabel;
	}
	public int getHours() {
		return hours;
	}
	public void setHours(int hours) {
		this.hours = hours;
	}
	public int getMinutes() {
		return minutes;
	}
	public void setMinutes(int minutes) {
		this.minutes = minutes;
	}
	public String getComment() {
		return comment;
	}
	public void setComment(String comment) {
		this.comment = comment;
	}
	public boolean isTraining() {
		return isTraining;
	}
	public void setTraining(boolean isTraining) {
		this.isTraining = isTraining;
	}
	public String getSuborderSign() {
		return suborderSign;
	}
	public void setSuborderSign(String suborderSign) {
		this.suborderSign = suborderSign;
	}
	public String getOrderSign() {
		return orderSign;
	}
	public void setOrderSign(String orderSign) {
		this.orderSign = orderSign;
	}
	public String getDate() {
		return date;
	}
	public void setDate(String date) {
		this.date = date;
	}
	public double getCosts() {
		return costs;
	}
	public void setCosts(double costs) {
		this.costs = costs;
	}
}
