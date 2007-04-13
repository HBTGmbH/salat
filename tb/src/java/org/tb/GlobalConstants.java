package org.tb;


/**
 * Global constants for HBT timekeeping system.
 * 
 * @author oda
 *
 */
public class GlobalConstants {

	public static final int STARTING_YEAR = 2007;
	
	public static final double MIN_HOURS_PER_DAY = 0.01;
	public static final double MAX_HOURS_PER_DAY = 10.0;
	
	public static final double MAX_COSTS = 1.0E8;
	public static final double MAX_HOURLY_RATE = 1000.0;
	public static final double MAX_DEBITHOURS = 10000.0;
	public static final double MAX_OVERTIME = 10000.0;
	public static final double MIN_OVERTIME = -10000.0;
	
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
	public static final int CUSTOMERSHORTNAME_MAX_LENGTH = 12;
	public static final int CUSTOMERADDRESS_MAX_LENGTH = 256;
	
	public static final int CUSTOMERORDER_SIGN_MAX_LENGTH = 16;
	public static final int CUSTOMERORDER_DESCRIPTION_MAX_LENGTH = 256;
	public static final int CUSTOMERORDER_SHORT_DESCRIPTION_MAX_LENGTH = 20;
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
	public static final int EMPLOYEECONTRACT_OVERTIME_COMMENT_MAX_LENGTH = 256;
	
	public static final int EMPLOYEE_FIRSTNAME_MAX_LENGTH = 256;
	public static final int EMPLOYEE_LASTNAME_MAX_LENGTH = 256;
	public static final int EMPLOYEE_LOGINNAME_MAX_LENGTH = 5;
	public static final int EMPLOYEE_PASSWORD_MAX_LENGTH = 256;
	public static final int EMPLOYEE_PASSWORD_MIN_LENGTH = 6;
	public static final int EMPLOYEE_SIGN_MAX_LENGTH = 5;
	public static final int EMPLOYEE_STATUS_MAX_LENGTH = 16;
	
	public static final String EMPLOYEE_STATUS_BL = "bl";
	public static final String EMPLOYEE_STATUS_PL = "pl";
	public static final String EMPLOYEE_STATUS_MA = "ma";
	public static final String EMPLOYEE_STATUS_AV = "av";
	public static final String EMPLOYEE_STATUS_GF = "gf";
	public static final String EMPLOYEE_STATUS_RESTRICTED = "restricted";
	public static final String EMPLOYEE_STATUS_ADM = "adm";
	
	public static final String MONTH_SHORTFORM_JANUARY = "Jan";
	public static final String MONTH_SHORTFORM_FEBRURAY = "Feb";
	public static final String MONTH_SHORTFORM_MARCH = "Mar";
	public static final String MONTH_SHORTFORM_APRIL = "Apr";
	public static final String MONTH_SHORTFORM_MAY = "May";
	public static final String MONTH_SHORTFORM_JUNE = "Jun";
	public static final String MONTH_SHORTFORM_JULY = "Jul";
	public static final String MONTH_SHORTFORM_AUGUST = "Aug";
	public static final String MONTH_SHORTFORM_SEPTEMBER = "Sep";
	public static final String MONTH_SHORTFORM_OCTOBER = "Oct";
	public static final String MONTH_SHORTFORM_NOVEMBER = "Nov";
	public static final String MONTH_SHORTFORM_DECEMBER = "Dec";
	
	public static final int MONTH_INTVALUE_JANUARY = 1;
	public static final int MONTH_INTVALUE_FEBRURAY = 2;
	public static final int MONTH_INTVALUE_MARCH = 3;
	public static final int MONTH_INTVALUE_APRIL = 4;
	public static final int MONTH_INTVALUE_MAY = 5;
	public static final int MONTH_INTVALUE_JUNE = 6;
	public static final int MONTH_INTVALUE_JULY = 7;
	public static final int MONTH_INTVALUE_AUGUST = 8;
	public static final int MONTH_INTVALUE_SEPTEMBER = 9;
	public static final int MONTH_INTVALUE_OCTOBER = 10;
	public static final int MONTH_INTVALUE_NOVEMBER = 11;
	public static final int MONTH_INTVALUE_DECEMBER = 12;
	
	public static final String ALL_ORDERS = "ALL ORDERS";
	public static final String ALL_EMPLOYEES = "ALL EMPLOYEES";
	
	public static final String TIMEREPORT_STATUS_OPEN = "open";
	public static final String TIMEREPORT_STATUS_COMMITED = "commited";
	public static final String TIMEREPORT_STATUS_CLOSED = "closed";
	
	// view constants
	public static final String VIEW_DAILY = "day";
	public static final String VIEW_MONTHLY = "month";
	public static final String VIEW_WEEKLY = "week";
	public static final String VIEW_PROJECT = "project";
	public static final String VIEW_CUSTOM = "custom";
	
	// customer order signs
	public static final String CUSTOMERORDER_SIGN_ILL = "KRANK";
	public static final String CUSTOMERORDER_SIGN_VACATION = "URLAUB";
	public static final String CUSTOMERORDER_SIGN_EXTRA_VACATION = "S-URLAUB";
	public static final String CUSTOMERORDER_SIGN_REMAINING_VACATION = "RESTURLAUB";
	
    // matrix constants
    public static final int MATRIX_SPECIFICDATE_ALLORDERS_ALLEMPLOYEES = 1;
    public static final int MATRIX_SPECIFICDATE_SPECIFICORDERS_ALLEMPLOYEES = 2;
    public static final int MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES = 3;
    public static final int MATRIX_SPECIFICDATE_SPECIFICORDERS_SPECIFICEMPLOYEES = 4;
	
    public static final int MAX_SERIAL_BOOKING_DAYS = 20;
    
    public static final String WARNING_SORT_TIMEREPORT_NOT_IN_RANGE_FOR_EC = "timereportnotinrange";
    public static final String WARNING_SORT_TIMEREPORT_NOT_IN_RANGE_FOR_EO = "timereportnotinrangeforeo";
    
    public static final byte DEBITHOURS_UNIT_MONTH = 12;
    public static final byte DEBITHOURS_UNIT_YEAR = 1;
    public static final byte DEBITHOURS_UNIT_TOTALTIME = 0;
    
    // pathstrings
    public static final String ICONPATH = "/tb/images/TreeView/";
    
}
