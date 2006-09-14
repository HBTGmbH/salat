package org.tb.helper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;

import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;

/**
 * Helper class for employee handling which does not directly deal with persistence
 * 
 * @author oda
 *
 */
public class EmployeeHelper {

	/**
	 * fills up employee list dependent on status of employee currently logged in:
	 * 	- 'ma' will only see himself in all employee selection lists
	 *  - 'bl', 'pl' will see all employees in employee selection lists
	 * 
	 * @param loginEmployee
	 * @param ed - EmployeeDAO being used
	 * 
	 * @return List<Employee> - selection list
	 */
	public List<Employee> getEmployeeOptions(Employee loginEmployee, EmployeeDAO ed) {
		List<Employee> employees = ed.getEmployees();
		List<Employee> optionList = new ArrayList();
		
		for (Iterator iter = employees.iterator(); iter.hasNext();) {
			Employee emp = (Employee) iter.next();
			
			if(emp.getId() == loginEmployee.getId()) {
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
	 * @param employeename
	 * @return String[] - two-element array with first and last name
	 */
	public String[] splitEmployeename(String employeename) {
		String[] firstAndLastname = new String[2];
		StringTokenizer st = 
		       new StringTokenizer
		           (employeename.trim(), " ");
		
		int i=0;
		while((st.hasMoreTokens() && (i<2))){
		  firstAndLastname[i]= st.nextToken();
		  i++;		  
		  }
		return firstAndLastname;
	}
	
	/**
	 * finds current employee and corresponding contract
	 * 
	 * @param loginEmployee
	 * @param request
	 * @param ed - EmployeeDAO being used
	 * @param ecd - EmployeecontractDAO being used
	 * 
	 * @return Employeecontract ec
	 */
	public Employeecontract setCurrentEmployee(Employee loginEmployee, HttpServletRequest request,
			EmployeeDAO ed, EmployeecontractDAO ecd) {
		Employeecontract ec = null;
		
		if (request.getSession().getAttribute("currentEmployee") == null) {
			// just logged in				
			ec = ecd.getEmployeeContractByEmployeeId(loginEmployee.getId());
			List<Employee> employeeOptionList = getEmployeeOptions(loginEmployee, ed);

			request.getSession().setAttribute("employees",
					employeeOptionList);
			request.getSession().setAttribute("currentEmployee",
					loginEmployee.getName());
			request.getSession().setAttribute("currentOrder", "ALL ORDERS");
		} else {
			String currentEmployeeName = (String) request.getSession()
					.getAttribute("currentEmployee");
			String[] firstAndLast = splitEmployeename(currentEmployeeName);
			if (!currentEmployeeName.equalsIgnoreCase("ALL EMPLOYEES")) {
				ec = ecd.getEmployeeContractByEmployeeName(firstAndLast[0], firstAndLast[1]);
			} else {
				ec = ecd.getEmployeeContractByEmployeeId(loginEmployee.getId());
			}
		}
		
		return ec;
	}
}
