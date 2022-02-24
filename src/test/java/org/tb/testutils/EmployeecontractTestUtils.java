package org.tb.testutils;

import lombok.experimental.UtilityClass;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.util.DateUtils;

@UtilityClass
public class EmployeecontractTestUtils {

	public static Employeecontract createEmployeecontract(Employee employee) {
		Employeecontract ec = new Employeecontract();
		ec.setCreated(DateUtils.now());
		ec.setCreatedby("kd");
		ec.setDailyWorkingTime(8.0);
		ec.setEmployee(employee);
		ec.setValidFrom(DateUtils.parseOrNull("2017-01-01"));
		ec.setSupervisor(employee);
		
		return ec;
	}
}
