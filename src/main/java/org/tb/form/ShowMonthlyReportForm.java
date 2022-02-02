package org.tb.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * Form for showing timereports in 'monthly' display.
 *
 * @author oda
 */
@Getter
@Setter
public class ShowMonthlyReportForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -7694572231721775229L;

    private String month;
    private String year;
    private String employeename;
    private String order;
    private long orderId;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        month = null;
        employeename = null;
    }

}
