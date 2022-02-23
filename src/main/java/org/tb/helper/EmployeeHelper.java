package org.tb.helper;

import java.util.Objects;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Helper class for employee handling which does not directly deal with persistence
 *
 * @author oda
 */
public class EmployeeHelper {

    /**
     * fills up employee list dependent on status of employee currently logged in:
     * - 'ma' will only see himself in all employee selection lists
     * - 'bl', 'pl' will see all employees in employee selection lists
     */
    public List<Employee> getEmployeeOptions(Employee loginEmployee, EmployeeDAO ed) {
        List<Employee> employees = ed.getEmployees();
        List<Employee> optionList = new ArrayList<Employee>();
        for (Employee emp : employees) {
            if (Objects.equals(emp.getId(), loginEmployee.getId())) {
                optionList.add(emp);
            } else {
                if ((loginEmployee.getStatus().equalsIgnoreCase("bl")) ||
                        (loginEmployee.getStatus().equalsIgnoreCase("pl"))) {
                    optionList.add(emp);
                }
            }
        }
        return optionList;
    }

    /**
     * splits full employee name into first and last name
     *
     * @return String[] - two-element array with first and last name
     */
    public String[] splitEmployeename(String employeename) {
        String[] firstAndLastname = new String[2];
        StringTokenizer st =
                new StringTokenizer
                        (employeename.trim(), " ");

        int i = 0;
        while ((st.hasMoreTokens() && (i < 2))) {
            firstAndLastname[i] = st.nextToken();
            i++;
        }
        return firstAndLastname;
    }

    /**
     * finds current employee and corresponding contract
     */
    public Employeecontract setCurrentEmployee(Employee loginEmployee, HttpServletRequest request,
                                               EmployeeDAO ed, EmployeecontractDAO ecd) {

        Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");

        if (currentEmployeeContract == null) {
            currentEmployeeContract = loginEmployeeContract;
        }


        List<Employee> employeeOptionList = getEmployeeOptions(loginEmployee, ed);
        List<Employee> employeeWithContractList = ed.getEmployeesWithContracts();
        List<Employeecontract> employeeContracts = ecd.getVisibleEmployeeContractsForEmployee(loginEmployee);

        request.getSession().setAttribute("employeeswithcontract", employeeWithContractList);
        request.getSession().setAttribute("employees", employeeOptionList);
        request.getSession().setAttribute("employeecontracts", employeeContracts);
        request.getSession().setAttribute("currentEmployee", currentEmployeeContract.getEmployee().getName());
        request.getSession().setAttribute("currentEmployeeId", currentEmployeeContract.getEmployee().getId());
        request.getSession().setAttribute("currentOrder", "ALL ORDERS");

        return currentEmployeeContract;
    }

}
