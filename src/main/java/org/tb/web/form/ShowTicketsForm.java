package org.tb.web.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.tb.bdom.TicketViewDecorator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

/**
 * Form for showing Gira Tickets.
 *
 * @author mgo
 */
@Getter
@Setter
public class ShowTicketsForm extends ActionForm {
    private static final long serialVersionUID = 1L; // 4732501556199472624L;

    private long orderId;
    private long suborderId;
    private long newSuborderId;
    private String fromDate;
    private String untilDate;
    private List<TicketViewDecorator> decorators = new LinkedList<>();

    public void setOrderId(long orderId) {
        if (orderId != this.orderId) {
            this.orderId = orderId;
            this.suborderId = 0;
        }
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
