package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

public class GenerateMultipleEmployeeordersForm extends ActionForm {
    
    private static final long serialVersionUID = 1L;
    private String[] employeecontractIdArray;
    private Long customerOrderId;
    private Long suborderId;
    
    public String[] getEmployeecontractIdArray() {
        return employeecontractIdArray;
    }
    public void setEmployeecontractIdArray(String[] employeecontractIdArray) {
        this.employeecontractIdArray = employeecontractIdArray;
    }
    public Long getCustomerOrderId() {
        return customerOrderId;
    }
    public void setCustomerOrderId(Long customerOrderId) {
        this.customerOrderId = customerOrderId;
    }
    public Long getSuborderId() {
        return suborderId;
    }
    public void setSuborderId(Long suborderId) {
        this.suborderId = suborderId;
    }
    
    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        setCustomerOrderId(-1L);
        setSuborderId(-1L);
    }
    
    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        // actually, no checks here
        return errors;
    }
}
