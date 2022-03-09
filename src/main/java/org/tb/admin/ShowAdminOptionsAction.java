package org.tb.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.employee.domain.Employee;
import org.tb.order.Employeeorder;
import org.tb.order.EmployeeorderDAO;

@Component
@RequiredArgsConstructor
public class ShowAdminOptionsAction extends LoginRequiredAction<ShowAdminOptionsForm> {

    private final TimereportDAO timereportDAO;
    private final EmployeeorderDAO employeeorderDAO;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping,
                                                 ShowAdminOptionsForm form, HttpServletRequest request,
                                                 HttpServletResponse response) throws Exception {

        request.getSession().removeAttribute("setemployeeorderresults");
        request.getSession().removeAttribute("unassignedreports");
        request.getSession().removeAttribute("problems");

        if ((request.getParameter("task") != null)
                && (request.getParameter("task").equals("SetEmployeeOrderInTimereports"))) {

            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            List<String> problems = new ArrayList<>();

            List<Timereport> timereports = Collections.emptyList(); // FIXME is this feature still in use???
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

            stopWatch.stop();

            request.getSession().setAttribute("setemployeeorderresults", "result:  total reports: " + total + " updated: " + updated + " unassignable: " + unassignable + " unaltered: " + assigned + "    duration: " + stopWatch.shortSummary());
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
