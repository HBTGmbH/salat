package org.tb.persistence.utils;

import org.tb.bdom.Employee;

/**
 * Helper methods for test cases concerning Employee-objects
 * 
 * @author kd
 *
 */
public class TestUtilsEmployee {
	
	/**
	 * creates a basic Employee-object, filled with test data
	 * 
	 * @param sign
	 * @return
	 */
	public static Employee createEmployee(String sign) {
		Employee employee = new Employee();
		
		employee.setFirstname(sign + "y");
		employee.setLastname(sign + "mann");
		employee.setLoginname(sign);
		employee.setStatus("ma");
		employee.setSign(sign);
		employee.setGender('m');
		
		employee.resetPassword();
		
		return employee;
	}
}
