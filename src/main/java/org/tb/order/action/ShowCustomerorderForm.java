package org.tb.order.action;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * Form for showing all customer orders.
 * Actually not used - will be needed if we want to have an editable customer orders display
 * (like the timereport daily display)
 *
 * @author oda
 */
@Getter
@Setter
public class ShowCustomerorderForm extends ActionForm {
    private static final long serialVersionUID = 1L; // 1906438218934586588L;

    private Boolean show;
    private String filter;
    private Long customerId;
    private Boolean showActualHours = false;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        show = false;
        filter = "";
        customerId = -1L;
        showActualHours = false;
    }

}
