package org.tb.web.form;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.TicketViewDecorator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedList;
import java.util.List;

/**
 * Form for showing Gira Tickets.
 *
 * @author mgo
 */
public class ShowTicketsForm extends ActionForm {


    private static final long serialVersionUID = 1L; // 4732501556199472624L;

    private long orderId;
    private long suborderId;
    private long newSuborderId;
    private String fromDate;
    private String untilDate;
    private List<TicketViewDecorator> decorators;


    public ShowTicketsForm() {
        decorators = new LinkedList<TicketViewDecorator>();
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {

        if (orderId != this.orderId) {
            this.orderId = orderId;
            this.suborderId = 0;
        }
    }

    public long getSuborderId() {
        return suborderId;
    }

    public void setSuborderId(long subOrderId) {
        this.suborderId = subOrderId;
    }

    public long getNewSuborderId() {
        return newSuborderId;
    }

    public void setNewSuborderId(long newSuborder) {
        this.newSuborderId = newSuborder;
    }

    public List<TicketViewDecorator> getTicketDecorators() {
        return decorators;
    }

    public void setTicketDecorators(List<TicketViewDecorator> decorators) {
        this.decorators = decorators;
    }

    public String getFromDate() {
        return fromDate;
    }

    public void setFromDate(String fromDate) {
        this.fromDate = fromDate;
    }

    public String getUntilDate() {
        return untilDate;
    }

    public void setUntilDate(String untilDate) {
        this.untilDate = untilDate;
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
    }

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();

        // actually, no checks here
        return errors;
    }

    @Nullable
    public TicketViewDecorator getTicketDecoratorWithId(@Nonnull Long ticketId) {

        for (TicketViewDecorator decorator : decorators) {
            if (ticketId.equals(decorator.getId())) {
                return decorator;
            }
        }
        return null;
    }

}
