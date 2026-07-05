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
  AA_NEEDS_PEOPLE_LEAD("AA-0006", "not authorized. People Lead level required!"),
  AA_NOT_ATHORIZED("AA-9999", "not authorized."),

  CO_UPDATE_GOT_VETO("CO-0001", "customer order cannot be changed due to veto"),
  CO_DELETE_GOT_VETO("CO-0002", "customer order cannot be deleted due to veto"),
  CO_RESPONSIBLE_HBT_REQUIRED("CO-0003", "responsible HBT employee is required"),
  CO_RESP_CONTRACT_EMPLOYEE_REQUIRED("CO-0004", "responsible HBT contract employee is required"),
  CO_NOT_FOUND("CO-0005", "customer order was not found"),

  CU_DELETE_GOT_VETO("CU-0001", "customer cannot be deleted due to veto"),
  CU_NOT_FOUND("CU-0002", "the customer was not found!"),
  CU_DUPLICATE_SHORT_NAME("CU-0003", "customer with same short name already exists!"),
  CU_SEGMENT_NOT_FOUND("CU-0004", "customer segment not found"),

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
  EM_ANONYMIZE_WRONG_SIGN("EM-0002", "confirm sign does not match the employee sign"),
  EM_NOT_FOUND("EM-0003", "employee was not found"),

  EO_UPDATE_GOT_VETO("EO-0001", "employee order cannot be changed due to veto"),
  EO_DELETE_GOT_VETO("EO-0002", "employee order cannot be deleted due to veto"),
  EO_CONFLICT_RESOLUTION_GOT_VETO("EO-0003", "conflict resolution cannot be performed due to veto"),

  SO_UPDATE_GOT_VETO("SO-0001", "suborder cannot be changed due to veto"),
  SO_DELETE_GOT_VETO("SO-0002", "suborder cannot be deleted due to veto"),
  SO_PARENTORDER_CYCLE("SO-0003", "parent would introduce a cycle or self-reference in the suborder hierarchy"),
  SO_NOT_FOUND("SO-0004", "suborder was not found"),

  TR_TIME_REPORT_NOT_FOUND("TR-0001", "timereportId must match a timereport"),
  TR_EMPLOYEE_CONTRACT_NOT_FOUND("TR-0002", "employeeContractById must match an employee contract"),
  TR_EMPLOYEE_ORDER_NOT_FOUND("TR-0003","employeeOrderId must match an employee order"),
  TR_REFERENCE_DAY_NULL("TR-0004","reference day must not be null"),
  TR_TASK_DESCRIPTION_INVALID_LENGTH("TR-0005","taskDescription out of valid length range"),
  TR_DURATION_HOURS_INVALID("TR-0006","durationHours must be 0 at minimum"),
  TR_DURATION_MINUTES_INVALID("TR-0007","durationMinutes must be 0 at minimum"),
  TR_DURATION_INVALID("TR-0008","At least one of durationHours and durationMinutes must be greater than 0"),
  TR_SEQUENCE_NUMBER_ALREADY_SET("TR-0011","sequencenumber already set on timereport"),
  TR_CLOSED_TIME_REPORT_REQ_MANAGER("TR-0012","closed time reports can only be saved by managers."),
  TR_COMMITTED_TIME_REPORT_REQ_MANAGER("TR-0013","committed time reports can only be saved by managers."),
  TR_OPEN_TIME_REPORT_REQ_EMPLOYEE("TR-0014","open time reports can only be saved by the employee herself."),
  TR_MONTH_BUDGET_EXCEEDED("TR-0015","debit minutes of employee order exceeded for month"),
  TR_YEAR_BUDGET_EXCEEDED("TR-0017","debit minutes of employee order exceeded for year"),
  TR_TOTAL_BUDGET_EXCEEDED("TR-0018","debit minutes of employee order exceeded (total)"),
  TR_SUBORDER_COMMENT_MANDATORY("TR-0019","taskDescription must not be empty to meet the requirements of the related suborder"),
  TR_EMPLOYEE_ORDER_INVALID_REF_DATE("TR-0020","referenceday must fit to the employee order's date validity - check also suborder and customer order"),
  TR_EMPLOYEE_CONTRACT_INVALID_REF_DATE("TR-0021","employee contract must be valid for the reference day of the time report"),
  TR_YEAR_OUT_OF_RANGE("TR-0022","Time reports must be modified only in the current, the previous or the next year"),
  TR_WORKING_DAY_START_NULL("TR-0024","the start of the working day must not be null"),
  TR_WORKING_DAY_NOT_WORKED("TR-0025","the working day must not be 'not worked'"),
  TR_COMMITTED_TIME_REPORT_NOT_SELF("TR-0026","own time reports cannot be created or changed before the accepted date"),
  TR_TIMEREPORTS_EXIST_CANNOT_DELETE_OR_UPDATE_EMPLOYEE_ORDER("TR-0027","there are time reports that prevent the update or deletion of the employee order"),
  TR_MOVE_SOURCE_TARGET_SAME("TR-0028", "source and target suborder must be different"),
  TR_MOVE_DATE_RANGE_OUTSIDE_TARGET("TR-0029", "date range must fit within target suborder validity"),

  RL_RELEASE_NOT_ALLOWED("RL-0001", "release not allowed"),
  RL_ACCEPT_NOT_ALLOWED("RL-0002", "accept not allowed"),
  RL_RELEASE_DATE_INVALID("RL-0003", "release date is null or outside contract validity range"),
  RL_RELEASE_DATE_BEFORE_ACCEPTANCE("RL-0004", "release date must not be before the acceptance date"),
  RL_ACCEPTANCE_DATE_INVALID("RL-0005", "acceptance date is null or outside contract validity range"),
  RL_ACCEPTANCE_DATE_AFTER_RELEASE("RL-0006", "acceptance date must not be after the release date"),
  RL_ACCEPTANCE_DATE_MOVED_BACKWARDS("RL-0007", "acceptance date must not move backwards"),

  WD_NOT_WORKED_TIMEREPORTS_FOUND("WD-0001","time reports found, please move or delete first!"),
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

  SE_USER_NOT_FOUND("SE-0001", "salat user not found for current login"),

  BU_BUDGET_NOT_FOUND("BU-0001", "order budget not found"),
  BU_ADJUSTMENT_NOT_FOUND("BU-0002", "order budget adjustment not found"),
  BU_PRICING_NOT_FOUND("BU-0003", "order pricing not found"),
  BU_PRICING_OVERLAP("BU-0006", "overlapping order pricing record exists"),
  BU_EMPLOYEE_COST_OVERLAP("BU-0007", "overlapping employee cost record exists for same name"),
  BU_EMPLOYEE_COST_ASSIGNMENT_OVERLAP("BU-0008", "overlapping employee cost assignment exists for same scope"),
  BU_EMPLOYEE_COST_NOT_FOUND("BU-0004", "employee cost not found"),
  BU_EMPLOYEE_COST_ASSIGNMENT_NOT_FOUND("BU-0005", "employee cost assignment not found"),

  XX_UNHANDLED_SERVLET_EXCEPTION("XX-0001", "Unhandled servlet exception"),
  XX_DATA_MISSING("XX-0002", "Required data missing"),
  ;

  private final String code;
  private final String message;

  @Override
  public String toString() {
    return code + ": " + message;
  }

}
