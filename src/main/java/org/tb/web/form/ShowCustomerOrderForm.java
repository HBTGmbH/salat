package org.tb.web.form;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * Form for showing all customer orders.
 * Actually not used - will be needed if we want to have an editable customer orders display
 * (like the timereport daily display)
 *
 * @author oda
 */
public class ShowCustomerOrderForm extends ActionForm {

    /**
     *
     */
    private static final long serialVersionUID = 1L; // 1906438218934586588L;
    private Boolean show;
    private String filter;
    private Long customerId;

    private Boolean showActualHours = false;


    /**
     * @return the show
     */
    public Boolean getShow() {
        return show;
    }

    /**
     * @param show the show to set
     */
    public void setShow(Boolean show) {
        this.show = show;
    }

    /**
     * @return the filter
     */
    public String getFilter() {
        return filter;
    }

    /**
     * @param filter the filter to set
     */
    public void setFilter(String filter) {
        this.filter = filter;
    }

    /**
     * @return the customerId
     */
    public Long getCustomerId() {
        return customerId;
    }

    /**
     * @param customerId the customerId to set
     */
    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    /**
     * @return the showActualHours
     */
    public Boolean getShowActualHours() {
        return showActualHours;
    }

    /**
     * @param showActualHours the showActualHours to set
     */
    public void setShowActualHours(Boolean showActualHours) {
        this.showActualHours = showActualHours;
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        show = false;
        filter = "";
        customerId = -1L;
        showActualHours = false;
    }

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();

        // actually, no checks here
        return errors;
    }

}
