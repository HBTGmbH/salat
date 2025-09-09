package org.tb.common;

import java.util.Locale;

/**
 * Global Salat constants
 *
 * @author oda
 */
public class GlobalConstants {

    public static final String SYSTEM_SIGN = "system";

    public static final int STARTING_YEAR = 2007;

    public static final int MAX_HOURS_PER_DAY = 10;

    public static final int COMMENT_MAX_LENGTH = 32000;

    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DEFAULT_EXCEL_DATE_FORMAT = "dd.MM.yyyy";
    public static final String DEFAULT_EXCEL_DATETIME_FORMAT = "dd.MM.yyyy HH:mm:ss";
    public static final String DEFAULT_TIMEZONE_ID = "Europe/Berlin";
    public static final Locale DEFAULT_LOCALE = Locale.GERMAN;

    public static final int DEFAULT_VACATION_PER_YEAR = 30;
    public static final int MAX_VACATION_PER_YEAR = 100;

    public static final int CUSTOMERNAME_MAX_LENGTH = 255;
    public static final int CUSTOMERSHORTNAME_MAX_LENGTH = 12;
    public static final int CUSTOMERADDRESS_MAX_LENGTH = 255;

    public static final int CUSTOMERORDER_SIGN_MAX_LENGTH = 16;
    public static final int CUSTOMERORDER_DESCRIPTION_MAX_LENGTH = 255;
    public static final int CUSTOMERORDER_SHORT_DESCRIPTION_MAX_LENGTH = 20;
    public static final int CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH = 64;
    public static final int CUSTOMERORDER_ORDER_CUSTOMER_MAX_LENGTH = 64;

    public static final int SUBORDER_SIGN_MAX_LENGTH = 16;
    public static final int SUBORDER_DESCRIPTION_MAX_LENGTH = 2048;
    public static final int SUBORDER_SHORT_DESCRIPTION_MAX_LENGTH = 40;
    public static final int SUBORDER_SUBORDER_CUSTOMER_MAX_LENGTH = 30;

    public static final char YESNO_YES = 'Y';
    public static final char YESNO_NO = 'N';
    public static final char YESNO_UNDEFINED = 'U';

    public static final int EMPLOYEECONTRACT_TASKDESCRIPTION_MAX_LENGTH = 255;
    public static final int EMPLOYEECONTRACT_OVERTIME_COMMENT_MAX_LENGTH = 255;

    public static final int EMPLOYEE_FIRSTNAME_MAX_LENGTH = 255;
    public static final int EMPLOYEE_LASTNAME_MAX_LENGTH = 255;
    public static final int EMPLOYEE_LOGINNAME_MAX_LENGTH = 5;
    public static final int EMPLOYEE_SIGN_MAX_LENGTH = 5;
    public static final int EMPLOYEE_STATUS_MAX_LENGTH = 16;

    public static final String EMPLOYEE_STATUS_MA = "ma";
    public static final String EMPLOYEE_STATUS_BO = "bo";
    public static final String EMPLOYEE_STATUS_BL = "bl";
    public static final String EMPLOYEE_STATUS_PV = "pv";
    public static final String EMPLOYEE_STATUS_RESTRICTED = "restricted";
    public static final String EMPLOYEE_STATUS_ADM = "adm";

    public static final String EMPLOYEE_SIGN_ADM = "adm";

    // FIXME use SimpleDateFormat for this
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

    // FIXME use SimpleDateFormat for this
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

    // FIXME use Calendar for this
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
    public static final String TIMEREPORT_STATUS_COMMITED = "commited"; // TODO fix type commited -> committed (changes data in database
    public static final String TIMEREPORT_STATUS_CLOSED = "closed";

    // view constants
    public static final String VIEW_DAILY = "day";
    public static final String VIEW_MONTHLY = "month";
    public static final String VIEW_CUSTOM = "custom";

    // matrix constants;
    public static final int MATRIX_SPECIFICDATE_ALLORDERS_ALLEMPLOYEES = 1;
    public static final int MATRIX_SPECIFICDATE_SPECIFICORDERS_ALLEMPLOYEES = 2;
    public static final int MATRIX_SPECIFICDATE_ALLORDERS_SPECIFICEMPLOYEES = 3;
    public static final int MATRIX_SPECIFICDATE_SPECIFICORDERS_SPECIFICEMPLOYEES = 4;

    public static final int MAX_SERIAL_BOOKING_DAYS = 25;

    public static final byte DEBITHOURS_UNIT_MONTH = 12;
    public static final byte DEBITHOURS_UNIT_YEAR = 1;
    public static final byte DEBITHOURS_UNIT_TOTALTIME = 0;

    // pathstrings and iconstrings
    public static final String ICONPATH = "/images/";
    public static final String CLOSEICON = "plus_circle.gif";
    public static final String OPENICON = "minus_circle.gif";
    public static final String DELETEICON = "Delete.gif";
    public static final String EDITICON = "Edit.gif";
    public static final String NOTALLOWED = "verbot.gif";
    public static final String PARENTICON = "Smily_Krone.gif";

    public static final long MINUTES_PER_HOUR = 60;
    public static final long MINUTES_PER_DAY = MINUTES_PER_HOUR * 24;

    public static final char GENDER_MALE = 'm';
    public static final char GENDER_FEMALE = 'f';

    public static final char INVOICE_YES = 'Y';

    public static final String MAIL_DOMAIN = "hbt.de";

    public static final String ZERO_DHM = "00:00:00";
    public static final String ZERO_HM = "00:00";

    public static final int QUARTER_HOUR_IN_MINUTES = 15;

    public static final int REPORT_NAME_MAX_LENGTH = 200;

    public static final int SIX_HOURS_IN_MINUTES = 360;
    public static final int NINE_HOURS_IN_MINUTES = 540;
    public static final int BREAK_MINUTES_AFTER_SIX_HOURS = 30;
    public static final int BREAK_MINUTES_AFTER_NINE_HOURS = 45;

    public static final long WORKDAY_MAX_LENGTH_ALLOWED_IN_MINUTES = 10 * MINUTES_PER_HOUR;
    public static final long REST_PERIOD_IN_MINUTES = 11 * MINUTES_PER_HOUR;

    public static final int DEFAULT_WORK_DAY_START = 9;
    public static final String CUSTOMERORDER_SIGN_VACATION = "URLAUB";
}
