package org.tb.web.form;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * Form for showing all suborders.
 * Actually not used - will be needed if we want to have an editable suborders display
 * (like the timereport daily display)
 *
 * @author oda
 */
public class ShowSuborderForm extends ActionForm {

    /**
     *
     */
    private static final long serialVersionUID = 1L; // 3430200308525131845L;
    private Boolean show;
    private String[] suborderIdArray;
    private String suborderOption;
    private String suborderOptionValue;
    private String filter;
    private Long customerOrderId;
    private Boolean showstructure = false;
    private Boolean showActualHours = false;
    private Boolean noResetChoice;
    private Boolean fixedPrice;

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

    public Boolean getShowstructure() {
        return showstructure;
    }

    public void setShowstructure(Boolean showstructure) {
        this.showstructure = showstructure;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    /**
     * @return the customerOrderId
     */
    public Long getCustomerOrderId() {
        return customerOrderId;
    }

    /**
     * @param customerOrderId the customerOrderId to set
     */
    public void setCustomerOrderId(Long customerOrderId) {
        this.customerOrderId = customerOrderId;
    }

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
     * @param getSuborderIdArray
     */
    public String[] getSuborderIdArray() {
        return suborderIdArray;
    }

    /**
     * @param getSuborderIdArray the getSuborderIdArray to set
     */
    public void setSuborderIdArray(String[] suborderIdArray) {
        this.suborderIdArray = suborderIdArray;
    }

    public Boolean getNoResetChoice() {
        return noResetChoice;
    }

    public void setNoResetChoice(Boolean noResetChoice) {
        this.noResetChoice = noResetChoice;
    }

    public Boolean getFixedPrice() {
        return fixedPrice;
    }

    public void setFixedPrice(Boolean fixedPrice) {
        this.fixedPrice = fixedPrice;
    }

    public String getSuborderOption() {
        return suborderOption;
    }

    public void setSuborderOption(String suborderOption) {
        this.suborderOption = suborderOption;
    }

    public String getSuborderOptionValue() {
        return suborderOptionValue;
    }

    public void setSuborderOptionValue(String suborderOptionValue) {
        this.suborderOptionValue = suborderOptionValue;
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        filter = "";
        show = false;
        customerOrderId = -1L;
        showstructure = false;
        showActualHours = false;
        noResetChoice = false;
        fixedPrice = false;
    }

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();

        // actually, no checks here
        return errors;
    }
}
