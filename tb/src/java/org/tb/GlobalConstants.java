package org.tb;


/**
 * Global constants for HBT timekeeping system.
 * 
 * @author oda
 *
 */
public class GlobalConstants {

	public static final double MIN_HOURS_PER_DAY = 0.01;
	public static final double MAX_HOURS_PER_DAY = 10.0;
	
	public static final double MAX_COSTS = 1.0E8;
	public static final double MAX_HOURLY_RATE = 1000.0;
	public static final double MAX_DEBITHOURS = 10000.0;
	
	public static final int COMMENT_MAX_LENGTH = 256;
	public static final int STATUS_MAX_LENGTH = 64;
	
	public static final String DEFAULT_CURRENCY = "EUR";
	
	public static final int BEGINHOUR = 9;
	public static final int BEGINMINUTE = 0;
	public static final int ENDHOUR = 17;
	public static final int ENDMINUTE = 00;
	
	public static final int MINUTE_INCREMENT = 5;
	
	public static final int BREAK_MINUTES = 30;
	
	public static final int VACATION_PER_YEAR = 30;
	public static final int MAX_VACATION_PER_YEAR = 100;
	
	public static final int CUSTOMERNAME_MAX_LENGTH = 256;
	public static final int CUSTOMERADDRESS_MAX_LENGTH = 256;
	
	public static final int CUSTOMERORDER_SIGN_MAX_LENGTH = 16;
	public static final int CUSTOMERORDER_DESCRIPTION_MAX_LENGTH = 256;
	public static final int CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH = 64;
	public static final int CUSTOMERORDER_RESP_HBT_MAX_LENGTH = 64;
	public static final int CUSTOMERORDER_ORDER_CUSTOMER_MAX_LENGTH = 64;
	public static final int CUSTOMERORDER_CURRENCY_MAX_LENGTH = 64;
	
	public static final int SUBORDER_SIGN_MAX_LENGTH = 16;
	public static final int SUBORDER_DESCRIPTION_MAX_LENGTH = 256;
	public static final int SUBORDER_CURRENCY_MAX_LENGTH = 64;
	
	public static final int SUBORDER_INVOICE_YES = 'Y';
	public static final int SUBORDER_INVOICE_NO = 'N';
	public static final int SUBORDER_INVOICE_UNDEFINED = 'U';
	
	public static final int EMPLOYEEORDER_SIGN_MAX_LENGTH = 16;
	public static final int EMPLOYEEORDER_STATUS_MAX_LENGTH = 64;
	
	public static final int EMPLOYEECONTRACT_TASKDESCRIPTION_MAX_LENGTH = 256;
	
	public static final int EMPLOYEE_FIRSTNAME_MAX_LENGTH = 256;
	public static final int EMPLOYEE_LASTNAME_MAX_LENGTH = 256;
	public static final int EMPLOYEE_LOGINNAME_MAX_LENGTH = 5;
	public static final int EMPLOYEE_PASSWORD_MAX_LENGTH = 256;
	public static final int EMPLOYEE_SIGN_MAX_LENGTH = 5;
	public static final int EMPLOYEE_STATUS_MAX_LENGTH = 16;
	
	public static final String EMPLOYEE_STATUS_BL = "bl";
	public static final String EMPLOYEE_STATUS_PL = "pl";
	public static final String EMPLOYEE_STATUS_MA = "ma";
}
