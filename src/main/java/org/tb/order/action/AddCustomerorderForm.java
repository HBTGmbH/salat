package org.tb.order.action;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.Employee;

/**
 * Form for adding a customer order
 *
 * @author oda
 */
@Getter
@Setter
public class AddCustomerorderForm extends ActionForm {
    private static final long serialVersionUID = 1L; // 3158661302891965253L;

    private long id;
    private String sign;
    private String description;
    private String shortdescription;
    private String validFrom;
    private String validUntil;
    private String responsibleCustomerTechnical;
    private String responsibleCustomerContractually;
    private String orderCustomer;

    private String debithours;
    private Byte debithoursunit;

    private int statusreport;
    private Boolean hide;

    private Long customerId;
    private long employeeId;
    private long respContrEmployeeId;

    private String action;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        sign = "";
        description = "";
        shortdescription = "";
        validFrom = DateUtils.format(today()); // 'yyyy-mm-dd'
        validUntil = DateUtils.format(today()); // 'yyyy-mm-dd'
        responsibleCustomerTechnical = "";
        responsibleCustomerContractually = "";
        Long currentEmployeeId = (Long) request.getSession().getAttribute("currentEmployeeId");
        if (currentEmployeeId != null && currentEmployeeId != -1 && currentEmployeeId > 0) {
            employeeId = currentEmployeeId;
        } else {
            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
            if(loginEmployee != null) {
                employeeId = loginEmployee.getId();
            } else {
                employeeId = -1;
            }
        }
        respContrEmployeeId = employeeId;

        orderCustomer = "";
        debithours = null;
        debithoursunit = null;
        statusreport = 0;
        hide = false;
    }

}
