package org.tb.order.action;

import jakarta.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.employee.domain.Employeecontract;

/**
 * Form for showing all employee orders.
 * Actually not used - will be needed if we want to have an editable employee orders display
 * (like the timereport daily display)
 *
 * @author oda
 */
@Getter
@Setter
@Slf4j
public class ShowEmployeeOrderForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -6415687265295197319L;

    private long employeeContractId;
    private long orderId;
    private long suborderId;
    private String filter;
    private Boolean show = false;
    private Boolean showActualHours = false;

    @Nonnull
    public Boolean getShowActualHours() {
        return showActualHours != null && showActualHours;
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        if (currentEmployeeContract != null) {
            employeeContractId = currentEmployeeContract.getId();
        } else {
            try {
                Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
                employeeContractId = loginEmployeeContract.getId();
            } catch (Exception e) {
                log.error("reset threw exception.", e);
            }
        }

        filter = "";
        show = false;
        showActualHours = false;
    }



}
