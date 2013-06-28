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
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    
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
    public static final int SUBORDER_DESCRIPTION_MAX_LENGTH = 2048;
    public static final int SUBORDER_SHORT_DESCRIPTION_MAX_LENGTH = 40;
    public static final int SUBORDER_CURRENCY_MAX_LENGTH = 64;
    public static final int SUBORDER_SUBORDER_CUSTOMER_MAX_LENGTH = 30;
    
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
    public static final String EMPLOYEE_STATUS_PV = "pv";
    public static final String EMPLOYEE_STATUS_RESTRICTED = "restricted";
    public static final String EMPLOYEE_STATUS_ADM = "adm";
    
    public static final String MONTH_SHORTFORM_JANUARY = "Jan";
    public static final String MONTH_SHORTFORM_FEBRUARY = "Feb";
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
    public static final String[] MONTH_SHORTFORMS = new String[] {
            MONTH_SHORTFORM_JANUARY, MONTH_SHORTFORM_FEBRUARY, MONTH_SHORTFORM_MARCH,
            MONTH_SHORTFORM_APRIL, MONTH_SHORTFORM_MAY, MONTH_SHORTFORM_JUNE,
            MONTH_SHORTFORM_JULY, MONTH_SHORTFORM_AUGUST, MONTH_SHORTFORM_SEPTEMBER,
            MONTH_SHORTFORM_OCTOBER, MONTH_SHORTFORM_NOVEMBER, MONTH_SHORTFORM_DECEMBER
    };
    
    public static final String MONTH_LONGFORM_JANUARY = "January";
    public static final String MONTH_LONGFORM_FEBRUARY = "February";
    public static final String MONTH_LONGFORM_MARCH = "March";
    public static final String MONTH_LONGFORM_APRIL = "April";
    public static final String MONTH_LONGFORM_MAY = "May";
    public static final String MONTH_LONGFORM_JUNE = "June";
    public static final String MONTH_LONGFORM_JULY = "July";
    public static final String MONTH_LONGFORM_AUGUST = "August";
    public static final String MONTH_LONGFORM_SEPTEMBER = "September";
    public static final String MONTH_LONGFORM_OCTOBER = "October";
    public static final String MONTH_LONGFORM_NOVEMBER = "November";
    public static final String MONTH_LONGFORM_DECEMBER = "December";
    public static final String[] MONTH_LONGFORMS = new String[] {
            MONTH_LONGFORM_JANUARY, MONTH_LONGFORM_FEBRUARY, MONTH_LONGFORM_MARCH,
            MONTH_LONGFORM_APRIL, MONTH_LONGFORM_MAY, MONTH_LONGFORM_JUNE,
            MONTH_LONGFORM_JULY, MONTH_LONGFORM_AUGUST, MONTH_LONGFORM_SEPTEMBER,
            MONTH_LONGFORM_OCTOBER, MONTH_LONGFORM_NOVEMBER, MONTH_LONGFORM_DECEMBER
    };
    
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
    
    // suborder signs
    public static final String SUBORDER_SIGN_OVERTIME_COMPENSATION = "uesa00";
    
    // matrix constants;
    public static final int MATRIX_SPECIFICDATE_ALLORDERS_ALLEMPLOYEES = 1;
    public static final int MATRIX_SPECIFICDATE_SPECIFICORDERS_ALLEMPLOYEES = 2;
    public static final int MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES = 3;
    public static final int MATRIX_SPECIFICDATE_SPECIFICORDERS_SPECIFICEMPLOYEES = 4;
    
    public static final int MAX_SERIAL_BOOKING_DAYS = 25;
    
    public static final String WARNING_SORT_TIMEREPORT_NOT_IN_RANGE_FOR_EC = "timereportnotinrange";
    public static final String WARNING_SORT_TIMEREPORT_NOT_IN_RANGE_FOR_EO = "timereportnotinrangeforeo";
    
    public static final byte DEBITHOURS_UNIT_MONTH = 12;
    public static final byte DEBITHOURS_UNIT_YEAR = 1;
    public static final byte DEBITHOURS_UNIT_TOTALTIME = 0;
    
    public static final String OVERTIME_COMPENSATION_TEXT = "�berstundenausgleich";
    
    // pathstrings and iconstrings
    public static final String ICONPATH = "/tb/images/";
    public static final String CLOSEICON = "plus_circle.gif";
    public static final String OPENICON = "minus_circle.gif";
    public static final String FOLDERICON = "folder_closed.gif";
    public static final String DELETEICON = "Delete.gif";
    public static final String EDITICON = "Edit.gif";
    public static final String NOTALLOWED = "verbot.gif";
    public static final String PARENTICON = "Smily_Krone.gif";
    
    // status for the employee order content page
    public static final String CONTENT_STATUS_NEW = "new";
    public static final String CONTENT_STATUS_SAVED = "saved";
    public static final String CONTENT_STATUS_COMMITTED = "committed";
    
    // qm processes
    public static final String QM_PROCESS_OTHER = "main.qm.process.other";
    public static final String QM_PROCESS_PA01A_AUFTRAGSGENERIERUNG_WERKVERTRAG = "main.qm.process.pa01a";
    public static final String QM_PROCESS_PA01B_AUFTRAGSGENERIERUNG_DIENSTLEISTUNGSVERTRAG = "main.qm.process.pa01b";
    public static final String QM_PROCESS_PA09A_AUFTRAGSDURCHFUEHRUNG_WERKVERTRAG = "main.qm.process.pa09a";
    public static final String QM_PROCESS_PA09B_AUFTRAGSDURCHFUEHRUNG_DIENSTLEISTUNGSVERTRAG = "main.qm.process.pa09b";
    public static final String QM_PROCESS_PA09C_AUFTRAGSDURCHFUEHRUNG_1_MANN_GEWERK = "main.qm.process.pa09c";
    
    public static final int QM_PROCESS_ID_OTHER = 1;
    public static final int QM_PROCESS_ID_PA01A_AUFTRAGSGENERIERUNG_WERKVERTRAG = 2;
    public static final int QM_PROCESS_ID_PA01B_AUFTRAGSGENERIERUNG_DIENSTLEISTUNGSVERTRAG = 3;
    public static final int QM_PROCESS_ID_PA09A_AUFTRAGSDURCHFUEHRUNG_WERKVERTRAG = 4;
    public static final int QM_PROCESS_ID_PA09B_AUFTRAGSDURCHFUEHRUNG_DIENSTLEISTUNGSVERTRAG = 5;
    public static final int QM_PROCESS_ID_PA09C_AUFTRAGSDURCHFUEHRUNG_1_MANN_GEWERK = 6;
    
    public static final int FORM_MAX_CHAR_BIG_TEXTAREA = 2048;
    public static final int FORM_MAX_CHAR_TEXTAREA = 256;
    public static final int FORM_MAX_CHAR_NAME_TEXTFIELD = 64;
    public static final int FORM_MAX_CHAR_TEXTFIELD = 64;
    
    public static final Integer PHASE_ID_ORGANISATION = 1;
    public static final Integer PHASE_ID_SPECIFICATIO = 2;
    public static final Integer PHASE_ID_ANALYSIS = 3;
    public static final Integer PHASE_ID_REALIZATION = 4;
    public static final Integer PHASE_ID_ACCEPTANCE = 5;
    public static final Integer PHASE_ID_DELIVERY = 6;
    public static final Integer PHASE_ID_ROLLOUT = 7;
    public static final Integer PHASE_ID_FINISH = 8;
    
    public static final Integer STATUS_CONSTANT = 1;
    public static final Integer STATUS_ASCENDING = 2;
    public static final Integer STATUS_EXTREM_ASCENDING = 3;
    public static final Integer STATUS_DESCENDING = 4;
    public static final Integer STATUS_EXTREM_DESCENDING = 5;
    
    public static final Integer STATUSREPORT_SORT_PERIODICAL = 1;
    public static final Integer STATUSREPORT_SORT_EXTRA = 2;
    public static final Integer STATUSREPORT_SORT_FINAL = 3;
    
    public static final Integer MINUTES_PER_HOUR = 60;
    
    public static final Character GENDER_MALE = 'm';
    public static final Character GENDER_FEMALE = 'f';
    
    public static final Character INVOICE_YES = 'Y';
    public static final Character INVOICE_NO = 'N';
    public static final Character INVOICE_UNDEFINED = 'U';
    public static final String INVOICE_EXCEL_EXPORT_FILENAME = "SALAT_Rechnung.xls";
    public static final String INVOICE_EXCEL_SHEET_NAME = "SALAT Rechnung";
    public static final String INVOICE_EXCEL_CONTENT_TYPE = "application/msexcel";
    public static final String INVOICE_DATE_FORMAT = GlobalConstants.DEFAULT_DATE_FORMAT.replace('y', 'J').replace('d', 'T');
    
    public static final String MAIL_HOST = "***REMOVED***";
    public static final String MAIL_DOMAIN = "hbt.de";
    public static final String MAIL_NOREPLY_ADDRESS = "noreply@" + GlobalConstants.MAIL_DOMAIN;
    
    public static final String ZERO_DHM = "00:00:00";
    public static final String ZERO_HM = "00:00";
    
}
