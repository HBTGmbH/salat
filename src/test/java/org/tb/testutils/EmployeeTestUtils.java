package org.tb.testutils;

import lombok.experimental.UtilityClass;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.SalatUser;

/**
 * Helper methods for test cases concerning Employee-objects
 * 
 * @author kd
 *
 */
@UtilityClass
public class EmployeeTestUtils {

	public static final String BOSS_SIGN = "boss";
	public static final String TESTY_SIGN = "testy";
	
	/**
	 * creates a basic Employee-object, filled with test data
	 * 
	 * @param sign
	 * @return
	 */
	public static Employee createEmployee(String sign) {
		Employee employee = new Employee();
		
		// Create SalatUser for loginname and status
		SalatUser salatUser = new SalatUser();
		salatUser.setLoginname(sign);
		salatUser.setStatus("ma");
		employee.setSalatUser(salatUser);
		
		employee.setFirstname(sign + "y");
		employee.setLastname(sign + "mann");
		employee.setSign(sign);
		employee.setGender(GlobalConstants.GENDER_MALE);
		
		return employee;
	}
}
