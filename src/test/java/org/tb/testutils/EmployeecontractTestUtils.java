package org.tb.testutils;

import java.util.Date;
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
		ec.setValidFrom(DateUtils.parse("2017-01-01", (Date)null));
		ec.setSupervisor(employee);
		
		return ec;
	}
}
