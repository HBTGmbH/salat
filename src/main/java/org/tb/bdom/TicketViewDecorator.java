package org.tb.bdom;

import java.sql.Date;
import java.util.List;


public class TicketViewDecorator extends Ticket {

    private static final long serialVersionUID = 1L;

    private final Ticket ticket;

    private long pickedSuborderId;
    private Date pickedFromDate;
    private Date pickedUntilDate;
    private int index;
    private boolean error;
    private String errorMessage;

    public TicketViewDecorator(Ticket ticket) {
        this.ticket = ticket;
        this.error = false;
    }

    public Date getPickedFromDate() {
        return pickedFromDate;
    }

    public void setPickedFromDate(Date pickedFromDate) {
        this.pickedFromDate = pickedFromDate;
    }

    public Date getPickedUntilDate() {
        return pickedUntilDate;
    }

    public void setPickedUntilDate(Date pickedUntilDate) {
        this.pickedUntilDate = pickedUntilDate;
    }

    @Override
    public int hashCode() {
        return ticket.hashCode();
    }

    @Override
    public long getId() {
        return ticket.getId();
    }

    @Override
    public void setId(long id) {
        ticket.setId(id);
    }

    @Override
    public Suborder getSuborder() {
        return ticket.getSuborder();
    }

    @Override
    public void setSuborder(Suborder suborder) {
        ticket.setSuborder(suborder);
    }

    @Override
    public String getJiraTicketKey() {
        return ticket.getJiraTicketKey();
    }

    @Override
    public void setJiraTicketKey(String jiraTicketKey) {
        ticket.setJiraTicketKey(jiraTicketKey);
    }

    @Override
    public List<Timereport> getTimereports() {
        return ticket.getTimereports();
    }

    @Override
    public void setTimereports(List<Timereport> timereports) {
        ticket.setTimereports(timereports);
    }

    @Override
    public Date getFromDate() {
        return ticket.getFromDate();
    }

    @Override
    public void setFromDate(Date fromDate) {
        ticket.setFromDate(fromDate);
    }

    @Override
    public Date getUntilDate() {
        return ticket.getUntilDate();
    }

    @Override
    public void setUntilDate(Date untilDate) {
        ticket.setUntilDate(untilDate);
    }

    @Override
    public boolean equals(Object obj) {
        return ticket.equals(obj);
    }

    @Override
    public String toString() {
        return ticket.toString();
    }

    public Ticket getTicket() {
        return ticket;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public long getSuborderId() {
        return getSuborder().getId();
    }

    public boolean getError() {
        return error;
    }

    public void setError(String errorMessage) {
        this.error = true;
        this.errorMessage = errorMessage;
    }

    public void disableError() {
        this.error = false;
        this.errorMessage = "";
    }

    public long getPickedSuborderId() {
        return pickedSuborderId;
    }

    public void setPickedSuborderId(long suborderId) {
        this.pickedSuborderId = suborderId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

}
