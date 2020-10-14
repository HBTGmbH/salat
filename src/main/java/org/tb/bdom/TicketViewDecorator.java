package org.tb.bdom;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;

import java.sql.Date;

@Getter
@Setter
@RequiredArgsConstructor
public class TicketViewDecorator extends Ticket {

    private static final long serialVersionUID = 1L;

    @Delegate
    private final Ticket ticket;

    private long pickedSuborderId;
    private Date pickedFromDate;
    private Date pickedUntilDate;
    private int index;
    private boolean error;
    private String errorMessage;

    @Override
    public int hashCode() {
        return ticket.hashCode();
    }

    @Override
    public String toString() {
        return ticket.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return ticket.equals(obj);
    }

    public long getSuborderId() {
        return getSuborder().getId();
    }

    public void setError(String errorMessage) {
        this.error = true;
        this.errorMessage = errorMessage;
    }

    public void disableError() {
        this.error = false;
        this.errorMessage = "";
    }

}
