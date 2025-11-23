package org.tb.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ErrorCode {

  AA_REQUIRED("AA-0001", "not authenticated!"),
  AA_NEEDS_UNRESTRICTED("AA-0002", "not authorized. Unrestricted access required!"),
  AA_NEEDS_BACKOFFICE("AA-0003", "not authorized. Backoffice level required!"),
  AA_NEEDS_MANAGER("AA-0004", "not authorized. Manager level required!"),
  AA_NEEDS_ADMIN("AA-0005", "not authorized. Admin level required!"),
  AA_NOT_ATHORIZED("AA-9999", "not authorized."),

  CO_UPDATE_GOT_VETO("CO-0001", "customer order cannot be changed due to veto"),
  CO_DELETE_GOT_VETO("CO-0002", "customer order cannot be deleted due to veto"),

  CU_DELETE_GOT_VETO("CU-0001", "customer cannot be deleted due to veto"),
  CU_NOT_FOUND("CU-0002", "the customer was not found!"),
  CU_DUPLICATE_SHORT_NAME("CU-0003", "customer with same short name already exists!"),

  EC_UPDATE_GOT_VETO("EC-0001", "employee contract cannot be changed due to veto"),
  EC_DELETE_GOT_VETO("EC-0002","employee contract suborder be deleted due to veto"),
  EC_INVALID_DATE_RANGE("EC-0003", "employee contract has invalid date range"),
  EC_SUPERVISOR_INVALID("EC-0004", "supervisor for employee contract is invalid"),
  EC_OVERLAPS("EC-0005", "employee contract validity overlaps another employee contract of the same employee"),
  EC_EMPLOYEE_CONTRACT_NOT_FOUND("EC-0006","employeeContractId must match an employee contract"),
  EC_UNRESOLVABLE_CONFLICT_TOO_MANY_OVERLAPS("EC-0007", "employee contract validity overlaps too many other employee contracts"),
  EC_CONFLICT_RESOLUTION_GOT_VETO("EC-0008", "conflict resolution cannot be performed due to veto"),
  EC_UNRESOLVABLE_CONFLICT_VALIDITY_SPLIT("EC-0009", "employee contract does not clearly overlap an existing but results in a split."),

  EM_DELETE_GOT_VETO("EM-0001", "employee cannot be deleted due to veto"),

  EO_UPDATE_GOT_VETO("EO-0001", "employee order cannot be changed due to veto"),
  EO_DELETE_GOT_VETO("EO-0002", "employee order cannot be deleted due to veto"),
  EO_CONFLICT_RESOLUTION_GOT_VETO("EO-0003", "conflict resolution cannot be performed due to veto"),

  SO_UPDATE_GOT_VETO("SO-0001", "suborder cannot be changed due to veto"),
  SO_DELETE_GOT_VETO("SO-0002", "suborder cannot be deleted due to veto"),

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
  TR_COMMITTED_TIME_REPORT_NOT_SELF("TR-0026","own time reports cannot be created or changed before the accepted date"),
  TR_TIMEREPORTS_EXIST_CANNOT_DELETE_OR_UPDATE_EMPLOYEE_ORDER("TR-0027","there are time reports that prevent the update or deletion of the employee order"),

  RL_RELEASE_NOT_ALLOWED("RL-0001", "release not allowed"),
  RL_ACCEPT_NOT_ALLOWED("RL-0002", "accept not allowed"),

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
  WD_OUTSIDE_CONTRACT("WD-0011", "the date is outside the validity of the employee contract!"),
  WD_DELETE_REQ_EMPLOYEE_OR_MANAGER("WD-0012", "you can only delete your own working days or you must be a manager!"),
  WD_READ_REQ_EMPLOYEE_OR_MANAGER("WD-0013", "you can only read your own working days or you must be a manager!"),

  ETL_INVALID_DATE_RANGE("ETL-0001", "etl definition executed with invalid date range"),

  XX_UNHANDLED_SERVLET_EXCEPTION("XX-0001", "Unhandled servlet exception"),
  ;

  private final String code;
  private final String message;

  @Override
  public String toString() {
    return code + ": " + message;
  }

}
