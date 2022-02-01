package org.tb.web.action.admin;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.*;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.web.form.ShowEmployeeOrderForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * action class for deleting an employee order
 *
 * @author oda
 */
public class DeleteEmployeeorderAction extends EmployeeOrderAction<ShowEmployeeOrderForm> {

    private EmployeeorderDAO employeeorderDAO;
    private EmployeecontractDAO employeecontractDAO;
    private TimereportDAO timereportDAO;

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }

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
