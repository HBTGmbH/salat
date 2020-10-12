package org.tb.persistence.utils;

import java.time.LocalDate;
import java.util.Date;

import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;

public class TestUtilsEmployeecontract {

	public static Employeecontract createEmployeecontract(Employee employee) {
		Employeecontract ec = new Employeecontract();
		ec.setCreated(new Date());
		ec.setCreatedby("kd");
		ec.setDailyWorkingTime(8.0);
		ec.setEmployee(employee);
		ec.setValidFrom(java.sql.Date.valueOf(LocalDate.of(2017, 1, 1)));
		ec.setSupervisor(employee);
		
		return ec;
	}
}
