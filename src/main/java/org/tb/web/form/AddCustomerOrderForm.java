package org.tb.web.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.util.DateUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * Form for adding a customer order
 *
 * @author oda
 */
@Getter
@Setter
public class AddCustomerOrderForm extends ActionForm {
    private static final long serialVersionUID = 1L; // 3158661302891965253L;

    private long id;
    private String sign;
    private String jiraProjectID;
    private String description;
    private String shortdescription;
    private String validFrom;
    private String validUntil;
    private String responsibleCustomerTechnical;
    private String responsibleCustomerContractually;
    private String orderCustomer;
    private String currency;
    private Double hourlyRate;

    private Double debithours;
    private Byte debithoursunit;

    private int statusreport;
    private Boolean hide;

    private long customerId;
    private long employeeId;
    private long respContrEmployeeId;

    private String action;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        sign = "";
        jiraProjectID = "";
        description = "";
        shortdescription = "";
        validFrom = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
        validUntil = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
        responsibleCustomerTechnical = "";
        responsibleCustomerContractually = "";
        if (null != request.getSession().getAttribute("currentEmployeeId") && (Long) request.getSession().getAttribute("currentEmployeeId") != -1
                && (Long) request.getSession().getAttribute("currentEmployeeId") != 0) {
            employeeId = (Long) request.getSession().getAttribute("currentEmployeeId");
        } else {
            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
            employeeId = loginEmployee.getId();

        }
        respContrEmployeeId = employeeId;

        orderCustomer = "";
        currency = GlobalConstants.DEFAULT_CURRENCY;
        hourlyRate = 0.0;
        debithours = null;
        debithoursunit = null;
        statusreport = 0;
        hide = false;
    }

}
