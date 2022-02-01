package org.tb.web.action.admin;

import org.apache.struts.action.*;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddEmployeeForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * action class for storing an employee permanently
 *
 * @author oda
 */
public class StoreEmployeeAction extends LoginRequiredAction<AddEmployeeForm> {

    private EmployeeDAO employeeDAO;

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddEmployeeForm emForm, HttpServletRequest request, HttpServletResponse response) {

        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("save")) ||
                (request.getParameter("emId") != null)) {

            // 'main' task - prepare everything to store the employee.
            // I.e., copy properties from the form into the employee before saving.
            long emId;
            Employee em;
            boolean create = false;

            if (request.getSession().getAttribute("emId") != null) {
                // edited employee
                emId = Long.parseLong(request.getSession().getAttribute("emId").toString());
                em = employeeDAO.getEmployeeById(emId);
            } else {
                // new report
                em = new Employee();
                create = true;
            }

            ActionMessages errorMessages = validateFormData(request, emForm);
            if (errorMessages.size() > 0) {
                return mapping.getInputForward();
            }

            em.setFirstname(emForm.getFirstname());
            em.setLastname(emForm.getLastname());
            em.setLoginname(emForm.getLoginname());
//				em.setPassword(emForm.getPassword());
            em.setStatus(emForm.getStatus());
            em.setSign(emForm.getSign());
            em.setGender(emForm.getGender().charAt(0));

            if (create) {
                em.resetPassword();
            }

            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

            employeeDAO.save(em, loginEmployee);

            request.getSession().setAttribute("employees", employeeDAO.getEmployees());

            boolean addMoreEmployees = Boolean.parseBoolean(request.getParameter("continue"));
            if (!addMoreEmployees) {
                request.getSession().removeAttribute("emId");
                String filter = null;

                if (request.getSession().getAttribute("employeeFilter") != null) {
                    filter = (String) request.getSession().getAttribute("employeeFilter");
                }

                request.getSession().setAttribute("employees", employeeDAO.getEmployeesByFilter(filter));

                return mapping.findForward("success");
            } else {
                emForm.reset(mapping, request);
                request.getSession().setAttribute("emId", null);
                return mapping.findForward("reset");
            }

        }

        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("resetPassword"))) {
            long emId;
            Employee em = null;
            if (request.getSession().getAttribute("emId") != null) {
                // edited employee
                emId = Long.parseLong(request.getSession().getAttribute("emId").toString());
                em = employeeDAO.getEmployeeById(emId);
            }
            if (em != null) {
                em.resetPassword();
                Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
                employeeDAO.save(em, loginEmployee);
            }

            return mapping.findForward("reset");
        }

        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("back"))) {
            // go back
            request.getSession().removeAttribute("emId");
            emForm.reset(mapping, request);
            return mapping.findForward("cancel");
        }
        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("reset"))) {
            // reset form
            doResetActions(mapping, request, emForm);
            return mapping.getInputForward();
        }

        return mapping.findForward("error");

    }

    /**
     * resets the 'add report' form to default values
     */
    private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddEmployeeForm emForm) {
        emForm.reset(mapping, request);
    }

    /**
     * validates the form data (syntax and logic)
     */
    private ActionMessages validateFormData(HttpServletRequest request, AddEmployeeForm emForm) {

        ActionMessages errors = getErrors(request);
        if (errors == null) errors = new ActionMessages();

        // for a new employee, check if name already exists
        if (request.getSession().getAttribute("emId") == null) {
            List<Employee> allEmployees = employeeDAO.getEmployees();
            for (Employee em : allEmployees) {
                if ((em.getFirstname().equalsIgnoreCase(emForm.getFirstname())) &&
                        (em.getLastname().equalsIgnoreCase(emForm.getLastname()))) {
                    errors.add("lastname", new ActionMessage("form.employee.error.name.alreadyexists"));
                    break;
                }
            }
        }

        // check length of text fields and if they are filled
        if (emForm.getFirstname().length() > GlobalConstants.EMPLOYEE_FIRSTNAME_MAX_LENGTH) {
            errors.add("firstname", new ActionMessage("form.employee.error.firstname.toolong"));
        }
        if (emForm.getFirstname().length() <= 0) {
            errors.add("firstname", new ActionMessage("form.employee.error.firstname.required"));
        }

        if (emForm.getLastname().length() > GlobalConstants.EMPLOYEE_LASTNAME_MAX_LENGTH) {
            errors.add("lastname", new ActionMessage("form.employee.error.lastname.toolong"));
        }
        if (emForm.getLastname().length() <= 0) {
            errors.add("lastname", new ActionMessage("form.employee.error.lastname.required"));
        }

        if (emForm.getLoginname().length() > GlobalConstants.EMPLOYEE_LOGINNAME_MAX_LENGTH) {
            errors.add("loginname", new ActionMessage("form.employee.error.loginname.toolong"));
        }
        if (emForm.getLoginname().length() <= 0) {
            errors.add("loginname", new ActionMessage("form.employee.error.loginname.required"));
        }

        if (emForm.getStatus().length() > GlobalConstants.EMPLOYEE_STATUS_MAX_LENGTH) {
            errors.add("status", new ActionMessage("form.employee.error.status.toolong"));
        }
        if (emForm.getStatus().length() <= 0) {
            errors.add("status", new ActionMessage("form.employee.error.status.required"));
        }

        if (emForm.getSign().length() > GlobalConstants.EMPLOYEE_SIGN_MAX_LENGTH) {
            errors.add("sign", new ActionMessage("form.employee.error.sign.toolong"));
        }
        if (emForm.getSign().length() <= 0) {
            errors.add("sign", new ActionMessage("form.employee.error.sign.required"));
        }

        saveErrors(request, errors);

        return errors;
    }
}
