package org.tb.employee.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;

/**
 * action class for storing an employee permanently
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class StoreEmployeeAction extends LoginRequiredAction<AddEmployeeForm> {

    private final EmployeeService employeeService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddEmployeeForm emForm, HttpServletRequest request, HttpServletResponse response) {

        if ((request.getParameter("task") != null) &&
                (request.getParameter("task").equals("save")) ||
                (request.getParameter("emId") != null)) {

            // 'main' task - prepare everything to store the employee.
            // I.e., copy properties from the form into the employee before saving.
            long employeeId;
            Employee employee;
            boolean create = false;

            if (request.getSession().getAttribute("emId") != null) {
                // edited employee
                employeeId = Long.parseLong(request.getSession().getAttribute("emId").toString());
                employee = employeeService.getEmployeeById(employeeId);
            } else {
                // new report
                employee = new Employee();
                create = true;
            }

            ActionMessages errorMessages = validateFormData(request, emForm);
            if (!errorMessages.isEmpty()) {
                return mapping.getInputForward();
            }

            employee.setFirstname(emForm.getFirstname());
            employee.setLastname(emForm.getLastname());
            employee.setLoginname(emForm.getLoginname());
            employee.setStatus(emForm.getStatus());
            employee.setSign(emForm.getSign());
            employee.setGender(emForm.getGender().charAt(0));

            employeeService.createOrUpdate(employee);

            request.getSession().setAttribute("employees", employeeService.getAllEmployees());

            boolean addMoreEmployees = Boolean.parseBoolean(request.getParameter("continue"));
            if (!addMoreEmployees) {
                request.getSession().removeAttribute("emId");
                String filter = null;

                if (request.getSession().getAttribute("employeeFilter") != null) {
                    filter = (String) request.getSession().getAttribute("employeeFilter");
                }

                request.getSession().setAttribute("employees", employeeService.getEmployeesByFilter(filter));

                return mapping.findForward("success");
            } else {
                emForm.reset(mapping, request);
                request.getSession().setAttribute("emId", null);
                return mapping.findForward("reset");
            }

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
            List<Employee> allEmployees = employeeService.getAllEmployees();
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
