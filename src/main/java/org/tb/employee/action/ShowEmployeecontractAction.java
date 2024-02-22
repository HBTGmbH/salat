package org.tb.employee.action;

import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * action class for showing all employee contracts
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class ShowEmployeecontractAction extends LoginRequiredAction<ShowEmployeeContractForm> {

    private final EmployeecontractDAO employeecontractDAO;
    private final EmployeeDAO employeeDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
        ShowEmployeeContractForm contractForm, HttpServletRequest request,
        HttpServletResponse response) {

        request.getSession().setAttribute("employees", employeeDAO.getEmployees().stream()
                .filter(e -> !e.getLastname().startsWith("z_"))
                .toList());

        String filter = null;
        Boolean show = null;
        Long employeeId = null;

        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("refresh"))) {
            filter = contractForm.getFilter();
            request.getSession().setAttribute("employeeContractFilter", filter);

            show = contractForm.getShow();
            request.getSession().setAttribute("employeeContractShow", show);

            employeeId = contractForm.getEmployeeId();
            request.getSession().setAttribute("employeeContractEmployeeId", employeeId);
        } else {
            if (request.getSession().getAttribute("employeeContractFilter") != null) {
                filter = (String) request.getSession().getAttribute("employeeContractFilter");
                contractForm.setFilter(filter);
            }
            if (request.getSession().getAttribute("employeeContractShow") != null) {
                show = (Boolean) request.getSession().getAttribute("employeeContractShow");
                contractForm.setShow(show);
            }
            if (request.getSession().getAttribute("employeeContractEmployeeId") != null) {
                employeeId = (Long) request.getSession().getAttribute("employeeContractEmployeeId");
                contractForm.setEmployeeId(employeeId);
            }
        }

        var employeeContracts = employeecontractDAO.getEmployeeContractsByFilters(show, filter, employeeId).stream()
                .filter(ec -> !ec.getEmployee().getLastname().startsWith("z_"))
                .toList();
        request.getSession().setAttribute("employeecontracts", employeeContracts);

        if (request.getParameter("task") != null) {
            if (request.getParameter("task").equalsIgnoreCase("back")) {
                // back to main menu
                return mapping.findForward("backtomenu");
            } else {
                // forward to show employee contracts jsp
                return mapping.findForward("success");
            }
        } else {
            // forward to show employee contracts jsp
            return mapping.findForward("success");
        }
    }

}
