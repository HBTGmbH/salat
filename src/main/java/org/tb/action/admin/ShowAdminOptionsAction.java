package org.tb.action.admin;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.action.LoginRequiredAction;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Timereport;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.TimereportDAO;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ShowAdminOptionsAction extends LoginRequiredAction<ActionForm> {

    private TimereportDAO timereportDAO;
    private EmployeeorderDAO employeeorderDAO;

    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping,
                                                 ActionForm form, HttpServletRequest request,
                                                 HttpServletResponse response) throws Exception {

        request.getSession().removeAttribute("setemployeeorderresults");
        request.getSession().removeAttribute("unassignedreports");
        request.getSession().removeAttribute("problems");

        if ((request.getParameter("task") != null)
                && (request.getParameter("task").equals("SetEmployeeOrderInTimereports"))) {

            Date start = new Date();
            List<String> problems = new ArrayList<>();

            List<Timereport> timereports = timereportDAO.getOrderedTimereports();
            List<Timereport> unassignedTimereports = new ArrayList<>();
            int total = timereports.size();
            int unassignable = 0;
            int updated = 0;
            int assigned = 0;
            for (Timereport timereport : timereports) {
                if (timereport.getEmployeeorder() == null) {
                    List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate2(
                            timereport.getEmployeecontract().getId(),
                            timereport.getSuborder().getId(),
                            timereport.getReferenceday().getRefdate());
                    if (employeeorders != null && !employeeorders.isEmpty()) {

                        if (employeeorders.size() > 1) {
                            String problem = "TR [tr:" + timereport.getId() + " | ec:" + timereport.getEmployeecontract().getId() + " | emp:" +
                                    timereport.getEmployeecontract().getEmployee().getSign() + " | so:" + timereport.getSuborder().getId() +
                                    " | order:" + timereport.getSuborder().getCustomerorder().getSign() + " / " + timereport.getSuborder().getSign() +
                                    " | date:" + timereport.getReferenceday().getRefdate() + "]";
                            problems.add(problem);
                            for (Employeeorder employeeorder : employeeorders) {
                                problem = "EO [eo:" + employeeorder.getId() + " | ec:" + employeeorder.getEmployeecontract().getId() + " | emp:" +
                                        employeeorder.getEmployeecontract().getEmployee().getSign() +
                                        " | so:" + employeeorder.getSuborder().getId() + " | order:" + employeeorder.getSuborder().getCustomerorder().getSign() +
                                        " / " + employeeorder.getSuborder().getSign()
                                        + " | from:" + employeeorder.getFromDate() +
                                        " | until:" + employeeorder.getUntilDate() + "]";
                                problems.add(problem);
                            }
                            problems.add("---------------------------------------------------------------");
                            unassignable++;
                            unassignedTimereports.add(timereport);
                        } else {
                            timereport.setEmployeeorder(employeeorders.get(0));

                            // save timereport
                            Employee saveEmployee = new Employee();
                            saveEmployee.setSign("system");
                            timereportDAO.save(timereport, saveEmployee, false);

                            updated++;
                        }
                    } else {
                        unassignable++;
                        unassignedTimereports.add(timereport);
                    }
                } else {
                    assigned++;
                }
            }

            Date end = new Date();
            long millis = end.getTime() - start.getTime();
            long sec = millis / 1000;
            long min = sec / 60;
            sec = sec % 60;

            request.getSession().setAttribute("setemployeeorderresults", "result:  total reports: " + total + " updated: " + updated + " unassignable: " + unassignable + " unaltered: " + assigned + "    duration: " + min + ":" + sec + " minutes");
            request.getSession().setAttribute("unassignedreports", unassignedTimereports);
            request.getSession().setAttribute("problems", problems);

            return mapping.findForward("success");
        }

        if (request.getParameter("task") == null) {
            return mapping.findForward("success");
        }

        return mapping.findForward("error");
    }

}
