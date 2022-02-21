package org.tb.action.order;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.TimereportDAO;

/**
 * action class for deleting an employee order
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class DeleteEmployeeorderAction extends EmployeeOrderAction<ShowEmployeeOrderForm> {

    private final EmployeeorderDAO employeeorderDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final TimereportDAO timereportDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowEmployeeOrderForm oldEmployeeOrderForm, HttpServletRequest request, HttpServletResponse response) {
        if (!GenericValidator.isLong(request.getParameter("eoId"))) return mapping.getInputForward();

        long eoId = Long.parseLong(request.getParameter("eoId"));
        Employeeorder eo = employeeorderDAO.getEmployeeorderById(eoId);
        if (eo == null) return mapping.getInputForward();

        boolean deleted = employeeorderDAO.deleteEmployeeorderById(eoId);
        ActionMessages errors = new ActionMessages();
        if (!deleted) {
            errors.add(null, new ActionMessage("form.employeeorder.error.hasstatusreports"));
        }
        saveErrors(request, errors);

        // create form with necessary values
        ShowEmployeeOrderForm employeeOrderForm = new ShowEmployeeOrderForm();
        Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        if (employeecontract == null) {
            employeeOrderForm.setEmployeeContractId(-1);
        } else {
            employeeOrderForm.setEmployeeContractId(employeecontract.getId());
        }
        Long orderId = (Long) request.getSession().getAttribute("currentOrderId");
        employeeOrderForm.setOrderId(orderId);

        employeeOrderForm.setShowActualHours(oldEmployeeOrderForm.getShowActualHours());

        refreshEmployeeOrders(request, employeeOrderForm, employeeorderDAO, employeecontractDAO, timereportDAO);

        // back to employee order display jsp
        return mapping.getInputForward();
    }

}
