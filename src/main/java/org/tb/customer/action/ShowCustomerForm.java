package org.tb.customer.action;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * Form for showing all customers.
 * Actually not used - will be needed if we want to have an editable customers display
 * (like the timereport daily display)
 *
 * @author oda
 */
@Getter
@Setter
public class ShowCustomerForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -7614210631483022615L;

    private String filter;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        filter = "";
    }

}
