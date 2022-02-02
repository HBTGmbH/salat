package org.tb.action.admin;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.action.LoginRequiredAction;
import org.tb.form.ShowEmployeeContractForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * action class for showing all employee contracts
 *
 * @author oda
 */
public class ShowEmployeecontractAction extends LoginRequiredAction<ShowEmployeeContractForm> {

    private EmployeecontractDAO employeecontractDAO;
    private EmployeeDAO employeeDAO;

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
        ShowEmployeeContractForm contractForm, HttpServletRequest request,
        HttpServletResponse response) {

        request.getSession().setAttribute("employees", employeeDAO.getEmployees());

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

        request.getSession().setAttribute("employeecontracts", employeecontractDAO.getEmployeeContractsByFilters(show, filter, employeeId));


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
