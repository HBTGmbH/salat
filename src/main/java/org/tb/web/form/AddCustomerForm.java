package org.tb.web.form;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * Form for adding a customer
 *
 * @author oda
 */
public class AddCustomerForm extends ActionForm {

    /**
     *
     */
    private static final long serialVersionUID = 1L; // -1885904234427057601L;
    private long id;
    private String name;
    private String shortname;
    private String address;
    private String action;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }


    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortname() {
        return shortname;
    }

    public void setShortname(String shortname) {
        this.shortname = shortname;
    }


    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        name = "";
        shortname = "";
        address = "";
    }

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();

        // actually, no checks here
        return errors;
    }

}
