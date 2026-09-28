package de.hbt.salat.testutils;

import java.time.Duration;
import lombok.experimental.UtilityClass;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;

@UtilityClass
public class EmployeecontractTestUtils {

	public static Employeecontract createEmployeecontract(Employee employee, Employee supervisor) {
		Employeecontract ec = new Employeecontract();
		ec.setDailyWorkingTime(Duration.ofHours(8));
		ec.setVacationEntitlement(GlobalConstants.DEFAULT_VACATION_PER_YEAR);
		ec.setEmployee(employee);
		ec.setValidFrom(DateUtils.parse("2017-01-01"));
		if (supervisor != null) {
			ec.setSupervisors(java.util.List.of(supervisor));
		}
		return ec;
	}
}
