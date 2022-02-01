package org.tb.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.util.DateUtils;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import java.sql.Date;
import java.text.SimpleDateFormat;

/**
 * Form for adding an employee order
 *
 * @author oda
 */
@Getter
@Setter
public class AddEmployeeOrderForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -2418121509861779749L;

    private long id;
    private String sign;
    private String validFrom;
    private String validUntil;
    private Double debithours;
    private Byte debithoursunit;
    private String status;
    private String order;
    private String suborder;
    private Boolean showOnlyValid;
    private long orderId;
    private long suborderId;
    private Long employeeContractId;
    private String action;

    public void reset(ActionMapping mapping, HttpServletRequest request, boolean extraCall) {
        Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        if (currentEmployeeContract != null) {
            employeeContractId = currentEmployeeContract.getId();
        } else {
            try {
                Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
                employeeContractId = loginEmployeeContract.getId();
            } catch (Exception e) {
                mapping.findForward("login");
            }
        }

        sign = "";
        status = "";
        validFrom = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
        validUntil = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
        debithours = null;
        debithoursunit = null;
        if (!extraCall) {
            showOnlyValid = false;
        }
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        reset(mapping, request, false);
    }

    public void useDatesFromCustomerOrder(Customerorder customerorder) {
        if (customerorder == null) {
            return;
        }
        Date coFromDate = customerorder.getFromDate();
        Date coUntilDate = customerorder.getUntilDate();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        String coFromDateString = simpleDateFormat.format(coFromDate);
        String coUntilDateString;
        if (coUntilDate != null) {
            coUntilDateString = simpleDateFormat.format(coUntilDate);
        } else {
            coUntilDateString = "";
        }
        setValidFrom(coFromDateString);
        setValidUntil(coUntilDateString);
    }

    @Nonnull
    public Boolean getShowOnlyValid() {
        return showOnlyValid != null && showOnlyValid;
    }

}
