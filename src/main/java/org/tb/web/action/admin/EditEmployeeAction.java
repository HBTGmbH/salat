package org.tb.web.action.admin;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * action class for editing an employee
 *
 * @author oda
 */
public class EditEmployeeAction extends LoginRequiredAction {

    private EmployeeDAO employeeDAO;

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        AddEmployeeForm employeeForm = (AddEmployeeForm) form;
        long emId = Long.parseLong(request.getParameter("emId"));
        Employee em = employeeDAO.getEmployeeById(emId);
        request.getSession().setAttribute("emId", em.getId());

        // fill the form with properties of employee to be edited
        setFormEntries(request, employeeForm, em);

        // forward to employee add/edit form
        return mapping.findForward("success");
    }

    /**
     * fills employee form with properties of given employee
     */
    private void setFormEntries(HttpServletRequest request, AddEmployeeForm employeeForm, Employee em) {
        employeeForm.setFirstname(em.getFirstname());
        employeeForm.setLastname(em.getLastname());
        employeeForm.setLoginname(em.getLoginname());
        employeeForm.setPassword(em.getPassword());
        employeeForm.setSign(em.getSign());
        employeeForm.setStatus(em.getStatus());
        employeeForm.setGender(Character.toString(em.getGender()));

        request.getSession().setAttribute("employeeStatus", em.getStatus());
        request.getSession().setAttribute("gender", Character.toString(em.getGender()));
    }

}
