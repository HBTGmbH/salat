package org.tb.employee.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;

/**
 * action class for deleting an employee contract
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class DeleteEmployeecontractAction extends LoginRequiredAction<ActionForm> {

    private final EmployeecontractService employeecontractService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {

        if ((GenericValidator.isBlankOrNull(request.getParameter("ecId"))) ||
                (!GenericValidator.isLong(request.getParameter("ecId"))))
            return mapping.getInputForward();

        ActionMessages errors = new ActionMessages();
        long ecId = Long.parseLong(request.getParameter("ecId"));
        Employeecontract ec = employeecontractService.getEmployeecontractById(ecId);
        if (ec == null)
            return mapping.getInputForward();

        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        if (Objects.equals(ec.getEmployee().getId(), loginEmployee.getId())) {
            errors.add(null, new ActionMessage("form.employeecontract.error.delete.isloginemployee"));
            saveErrors(request, errors);
            return mapping.getInputForward();
        }

        boolean deleted = employeecontractService.deleteEmployeeContractById(ecId);

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

        request.getSession().setAttribute("employeecontracts", employeecontractService.getEmployeeContractsByFilters(show, filter, filterEmployeeId));

        // set current employee back to loginEmployee to make sure that current employee is not the
        // one whose contract was just deleted...
        request.getSession().setAttribute("currentEmployee", loginEmployee.getName());
        request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());

        // back to employee contract display jsp
        return mapping.getInputForward();
    }

}
