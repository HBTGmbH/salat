package org.tb.persistence.utils;

import java.util.Date;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.util.DateUtils;

public class TestUtilsEmployeecontract {

	public static Employeecontract createEmployeecontract(Employee employee) {
		Employeecontract ec = new Employeecontract();
		ec.setCreated(new Date());
		ec.setCreatedby("kd");
		ec.setDailyWorkingTime(8.0);
		ec.setEmployee(employee);
		ec.setValidFrom(DateUtils.parse("2017-01-01", (Date)null));
		ec.setSupervisor(employee);
		
		return ec;
	}
}
