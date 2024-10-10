package org.tb.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ErrorCode {

  EC_TIME_REPORTS_OUTSIDE_VALIDITY("EC-0001", "employee contract has time reports outside it's new validity"),
  EC_INVALID_DATE_RANGE("EC-0002", "employee contract has invalid date range"),
  EC_EFFECTIVE_EMPLOYEE_ORDER_OUTSIDE_VALIDITY("EC-0003", "effective employee order exists outside employee contract validity"),
  EC_SUPERVISOR_INVALID("EC-0004", "supervisor for employee contract is invalid"),
  EC_OVERLAPS("EC-0005", "employee contract validity overlaps another employee contract of the same employee"),
  EC_EMPLOYEE_CONTRACT_NOT_FOUND("EC-0006","employeeContractId must match an employee contract"),
  SO_TIMEREPORT_EXISTS_OUTSIDE_VALIDITY("SO-0001", "suborder validity does not include all existing time reports"),
  TR_TIME_REPORT_NOT_FOUND("TR-0001", "timereportId must match a timereport"),
  TR_EMPLOYEE_CONTRACT_NOT_FOUND("TR-0002", "employeeContractById must match an employee contract"),
  TR_EMPLOYEE_ORDER_NOT_FOUND("TR-0003","employeeOrderId must match an employee order"),
  TR_REFERENCE_DAY_NULL("TR-0004","reference day must not be null"),
  TR_TASK_DESCRIPTION_INVALID_LENGTH("TR-0005","taskDescription out of valid length range"),
  TR_DURATION_HOURS_INVALID("TR-0006","durationHours must be 0 at minimum"),
  TR_DURATION_MINUTES_INVALID("TR-0007","durationMinutes must be 0 at minimum"),
  TR_DURATION_INVALID("TR-0008","At least one of durationHours and durationMinutes must be greater than 0"),
  TR_SEQUENCE_NUMBER_ALREADY_SET("TR-0011","sequencenumber already set on timereport"),
  TR_CLOSED_TIME_REPORT_REQ_ADMIN("TR-0012","closed time reports can only be saved by admins."),
  TR_COMMITTED_TIME_REPORT_REQ_MANAGER("TR-0013","committed time reports can only be saved by admins and managers."),
  TR_OPEN_TIME_REPORT_REQ_EMPLOYEE("TR-0014","open time reports can only be saved by the employee herself."),
  TR_MONTH_BUDGET_EXCEEDED("TR-0015","debit minutes of employee order exceeded for month"),
  TR_YEAR_BUDGET_EXCEEDED("TR-0017","debit minutes of employee order exceeded for year"),
  TR_TOTAL_BUDGET_EXCEEDED("TR-0018","debit minutes of employee order exceeded (total)"),
  TR_SUBORDER_COMMENT_MANDATORY("TR-0019","taskDescription must not be empty to meet the requirements of the related suborder"),
  TR_EMPLOYEE_ORDER_INVALID_REF_DATE("TR-0020","referenceday must fit to the employee order's date validity - check also suborder and customer order"),
  TR_EMPLOYEE_CONTRACT_INVALID_REF_DATE("TR-0021","employee contract must be valid for the reference day of the time report"),
  TR_YEAR_OUT_OF_RANGE("TR-0022","Time reports must be modified only in the current, the previous or the next year"),
  TR_DURATION_OVERTIME_COMPENSATION_INVALID("TR-0023","Overtime compensations must always be booked with 0 time"),
  TR_WORKING_DAY_START_NULL("TR-0024","the start of the working day must not be null"),
  TR_WORKING_DAY_NOT_WORKED("TR-0025","the working day must not be 'not worked'"),
  WD_NOT_WORKED_TIMEREPORTS_FOUND("WD-0001","time reports found, please move or delete first!"),
  WD_SATSUN_NOT_WORKED("WD-0002","no one can compensate his overtime on saturdays or sundays!"),
  WD_HOLIDAY_NO_WORKED("WD-0003", "no one can compensate his overtime on a public holiday!"),
  WD_UPSERT_REQ_EMPLOYEE_OR_MANAGER("WD-0004", "you can only save your own working days or you must be a manager!"),
  WD_REST_TIME_TOO_SHORT("WD-0005", "the time to rest to the last working day has to be at least 11 hours!"),
  WD_BREAK_TOO_SHORT_6("WD-0006", "for more than 6 hours of work per day, you must have booked at least 30 minutes of break time!"),
  WD_BREAK_TOO_SHORT_9("WD-0007", "for more than 9 hours of work per day, you must have booked at least 45 minutes of break time!"),
  WD_BEGIN_TIME_MISSING("WD-0008", "the beginning of the working day was not entered!"),
  WD_NO_TIMEREPORT("WD-0009", "no time report found for workday."),
  WD_LENGTH_TOO_LONG("WD-0010", "the worked time for the working day exceeds 10 hours!"),
  ;

  private final String code;
  private final String message;

  @Override
  public String toString() {
    return code + ": " + message;
  }

}
