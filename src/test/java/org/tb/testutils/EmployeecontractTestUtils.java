package org.tb.testutils;

import java.time.Duration;
import lombok.experimental.UtilityClass;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;

@UtilityClass
public class EmployeecontractTestUtils {

	public static Employeecontract createEmployeecontract(Employee employee, Employee supervisor) {
		Employeecontract ec = new Employeecontract();
		ec.setDailyWorkingTime(Duration.ofHours(8));
		ec.setEmployee(employee);
		ec.setValidFrom(DateUtils.parse("2017-01-01"));
		ec.setSupervisor(supervisor);
		return ec;
	}
}
