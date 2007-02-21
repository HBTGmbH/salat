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
		List<Employee> optionList = new ArrayList<Employee>();
		
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
	
	
//	/**
//	 * fills up employee list dependent on status of employee currently logged in:
//	 * 	- 'ma' will only see himself in all employee selection lists
//	 *  - 'bl', 'pl' will see all employees in employee selection lists
//	 *  - only employees with employeecontracts will be shown
//	 * 
//	 * @param loginEmployee
//	 * @param ed - EmployeeDAO being used
//	 * @param ecd - EmployeecontractDAO being used
//	 * @return List<Employee> - selection list
//	 */
//	public List<Employee> getEmployeeWithContractsOptions(Employee loginEmployee, EmployeeDAO ed, EmployeecontractDAO ecd) {
//		List<Employee> employees = ed.getEmployees();
//		List<Employee> optionList = new ArrayList<Employee>();
//		
//		for (Iterator iter = employees.iterator(); iter.hasNext();) {
//			Employee emp = (Employee) iter.next();
//			
//			if((emp.getId() == loginEmployee.getId()) && (ecd.getEmployeeContractByEmployeeId(emp.getId()) != null)) {
//				optionList.add(emp);
//			} else {
//				if (((loginEmployee.getStatus().equalsIgnoreCase("bl")) ||
//					(loginEmployee.getStatus().equalsIgnoreCase("pl"))) && 
//					(ecd.getEmployeeContractByEmployeeId(emp.getId()) != null)) {
//					optionList.add(emp);
//				}
//			}
//		}
//		
//		return optionList;
//	}
	
	
	
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
		
		Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
		Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
		
//		Long currentEmployeeId = (Long) request.getSession().getAttribute("currentEmployeeId");
		
		if (currentEmployeeContract == null) {
			currentEmployeeContract = loginEmployeeContract;
		}
						
		
		List<Employee> employeeOptionList = getEmployeeOptions(loginEmployee, ed);
		List<Employee> employeeWithContractList = ed.getEmployeesWithContracts();
		List<Employeecontract> employeeContracts = ecd.getVisibleEmployeeContractsOrderedByEmployeeSign();
			
		request.getSession().setAttribute("employeeswithcontract", 
				employeeWithContractList);
		request.getSession().setAttribute("employees",
				employeeOptionList);
		request.getSession().setAttribute("employeecontracts",
				employeeContracts);
		request.getSession().setAttribute("currentEmployee",
				currentEmployeeContract.getEmployee().getName());
		request.getSession().setAttribute("currentEmployeeId", 
				currentEmployeeContract.getEmployee().getId());
		request.getSession().setAttribute("currentOrder", "ALL ORDERS");
			
		
		return currentEmployeeContract;
	}
}
