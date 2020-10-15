package org.tb.web.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * Form for adding a customer
 *
 * @author oda
 */
@Getter
@Setter
public class AddCustomerForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -1885904234427057601L;

    private long id;
    private String name;
    private String shortname;
    private String address;
    private String action;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        name = "";
        shortname = "";
        address = "";
    }

}
