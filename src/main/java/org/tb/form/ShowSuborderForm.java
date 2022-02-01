package org.tb.form;

import lombok.Getter;
import lombok.Setter;
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
@Getter
@Setter
public class ShowSuborderForm extends ActionForm {
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

}
