package org.tb.web.action.admin;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.*;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.web.action.LoginRequiredAction;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * action class for deleting an employee contract
 *
 * @author oda
 */
public class DeleteEmployeecontractAction extends LoginRequiredAction {

    private EmployeecontractDAO employeecontractDAO;

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {

        if ((GenericValidator.isBlankOrNull(request.getParameter("ecId"))) ||
                (!GenericValidator.isLong(request.getParameter("ecId"))))
            return mapping.getInputForward();

        ActionMessages errors = new ActionMessages();
        long ecId = Long.parseLong(request.getParameter("ecId"));
        Employeecontract ec = employeecontractDAO.getEmployeeContractById(ecId);
        if (ec == null)
            return mapping.getInputForward();

        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        if (ec.getEmployee().getId() == loginEmployee.getId()) {
            errors.add(null, new ActionMessage("form.employeecontract.error.delete.isloginemployee"));
            saveErrors(request, errors);
            return mapping.getInputForward();
        }

        boolean deleted = employeecontractDAO.deleteEmployeeContractById(ecId);

        if (!deleted) {
            errors.add(null, new ActionMessage("form.employeecontract.error.hasrelated"));
        }

        saveErrors(request, errors);

        String filter = null;
        Boolean show = null;
        Long filterEmployeeId = null;

        if (request.getSession().getAttribute("employeeContractFilter") != null) {
            filter = (String) request.getSession().getAttribute("employeeContractFilter");
        }
        if (request.getSession().getAttribute("employeeContractShow") != null) {
            show = (Boolean) request.getSession().getAttribute("employeeContractShow");
        }
        if (request.getSession().getAttribute("employeeContractEmployeeId") != null) {
            filterEmployeeId = (Long) request.getSession().getAttribute("employeeContractEmployeeId");
        }

        request.getSession().setAttribute("employeecontracts", employeecontractDAO.getEmployeeContractsByFilters(show, filter, filterEmployeeId));

        // set current employee back to loginEmployee to make sure that current employee is not the
        // one whose contract was just deleted...
        request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
        request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());

        // back to employee contract display jsp
        return mapping.getInputForward();
    }

}
