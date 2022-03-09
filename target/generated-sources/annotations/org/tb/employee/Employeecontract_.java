package org.tb.employee;

import java.time.Duration;
import java.time.LocalDate;
import javax.annotation.Generated;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.Vacation;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Employeeorder;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Employeecontract.class)
public abstract class Employeecontract_ extends org.tb.common.AuditedEntity_ {

	public static volatile SingularAttribute<Employeecontract, Duration> overtimeStaticMinutes;
	public static volatile SingularAttribute<Employeecontract, String> taskDescription;
	public static volatile SingularAttribute<Employeecontract, LocalDate> reportAcceptanceDate;
	public static volatile ListAttribute<Employeecontract, Employeeorder> employeeorders;
	public static volatile SingularAttribute<Employeecontract, LocalDate> reportReleaseDate;
	public static volatile ListAttribute<Employeecontract, Timereport> timereports;
	public static volatile SingularAttribute<Employeecontract, LocalDate> validFrom;
	public static volatile SingularAttribute<Employeecontract, Employee> employee;
	public static volatile SingularAttribute<Employeecontract, Boolean> hide;
	public static volatile SingularAttribute<Employeecontract, Duration> dailyWorkingTimeMinutes;
	public static volatile ListAttribute<Employeecontract, Vacation> vacations;
	public static volatile SingularAttribute<Employeecontract, LocalDate> fixedUntil;
	public static volatile SingularAttribute<Employeecontract, LocalDate> validUntil;
	public static volatile SingularAttribute<Employeecontract, Boolean> freelancer;
	public static volatile SingularAttribute<Employeecontract, Employee> supervisor;

	public static final String OVERTIME_STATIC_MINUTES = "overtimeStaticMinutes";
	public static final String TASK_DESCRIPTION = "taskDescription";
	public static final String REPORT_ACCEPTANCE_DATE = "reportAcceptanceDate";
	public static final String EMPLOYEEORDERS = "employeeorders";
	public static final String REPORT_RELEASE_DATE = "reportReleaseDate";
	public static final String TIMEREPORTS = "timereports";
	public static final String VALID_FROM = "validFrom";
	public static final String EMPLOYEE = "employee";
	public static final String HIDE = "hide";
	public static final String DAILY_WORKING_TIME_MINUTES = "dailyWorkingTimeMinutes";
	public static final String VACATIONS = "vacations";
	public static final String FIXED_UNTIL = "fixedUntil";
	public static final String VALID_UNTIL = "validUntil";
	public static final String FREELANCER = "freelancer";
	public static final String SUPERVISOR = "supervisor";

}

