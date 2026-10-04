package de.hbt.salat.common.exception;

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

  AR_NOT_FOUND("AR-0001", "authorization rule was not found"),
  AR_CATEGORY_REQUIRED("AR-0002", "a category is required"),
  AR_GRANTEE_REQUIRED("AR-0003", "at least one grantee is required"),
  AR_ACCESS_LEVEL_REQUIRED("AR-0004", "at least one access level is required"),
  AR_OBJECT_MALFORMED("AR-0005", "the object does not fit the format of this category"),
  AR_VALIDITY_INVALID("AR-0006", "the rule ends before it starts"),
  AR_VALUE_TOO_LONG("AR-0007", "the list of values is longer than the column holds"),
  AR_NAME_REQUIRED("AR-0008", "a name is required"),
  AR_NAME_TOO_LONG("AR-0009", "the name is longer than the column holds"),
  AR_NAME_TAKEN("AR-0010", "another authorization rule already has this name"),
  AR_GRANTEE_UNKNOWN("AR-0011", "the grantee is not a known login"),
  AR_OBJECT_UNRESOLVED("AR-0012", "the input does not name an existing record"),

  CO_UPDATE_GOT_VETO("CO-0001", "customer order cannot be changed due to veto"),
  CO_DELETE_GOT_VETO("CO-0002", "customer order cannot be deleted due to veto"),
  CO_RESPONSIBLE_HBT_REQUIRED("CO-0003", "responsible HBT employee is required"),
  CO_RESP_CONTRACT_EMPLOYEE_REQUIRED("CO-0004", "responsible HBT contract employee is required"),
  CO_NOT_FOUND("CO-0005", "customer order was not found"),
  CO_SIGN_TAKEN("CO-0006", "another customer order already has this sign"),
  CO_SPECIAL_ORDER_LOCKED("CO-0007", "the configuration names this customer order as a special order; its sign is locked"),

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
  EC_NO_CURRENT_CONTRACT("EC-0010", "no employee contract is valid today for the current login"),

  EM_DELETE_GOT_VETO("EM-0001", "employee cannot be deleted due to veto"),
  EM_ANONYMIZE_WRONG_SIGN("EM-0002", "confirm sign does not match the employee sign"),
  EM_NOT_FOUND("EM-0003", "employee was not found"),
  EM_NO_LOGIN_EMPLOYEE("EM-0004", "no employee matches the current login"),
  EM_SIGN_TAKEN("EM-0005", "another employee already has this sign"),
  EM_LOGINNAME_TAKEN("EM-0006", "another login already has this name"),

  EO_UPDATE_GOT_VETO("EO-0001", "employee order cannot be changed due to veto"),
  EO_DELETE_GOT_VETO("EO-0002", "employee order cannot be deleted due to veto"),
  EO_CONFLICT_RESOLUTION_GOT_VETO("EO-0003", "conflict resolution cannot be performed due to veto"),

  SO_UPDATE_GOT_VETO("SO-0001", "suborder cannot be changed due to veto"),
  SO_DELETE_GOT_VETO("SO-0002", "suborder cannot be deleted due to veto"),
  SO_PARENTORDER_CYCLE("SO-0003", "parent would introduce a cycle or self-reference in the suborder hierarchy"),
  SO_NOT_FOUND("SO-0004", "suborder was not found"),
  SO_PARENTORDER_INVALID("SO-0005", "parent suborder does not exist or belongs to another customer order"),
  SO_SIGN_TAKEN("SO-0006", "a sibling suborder already has this sign"),
  SO_SPECIAL_ORDER_LOCKED("SO-0007", "the configuration names this suborder, or one below it, as a special order; its complete sign is locked"),

  TR_TIME_REPORT_NOT_FOUND("TR-0001", "timereportId must match a timereport"),
  TR_EMPLOYEE_CONTRACT_NOT_FOUND("TR-0002", "employeeContractById must match an employee contract"),
  TR_EMPLOYEE_ORDER_NOT_FOUND("TR-0003","employeeOrderId must match an employee order"),
  TR_REFERENCE_DAY_NULL("TR-0004","reference day must not be null"),
  TR_TASK_DESCRIPTION_INVALID_LENGTH("TR-0005","taskDescription out of valid length range"),
  TR_DURATION_HOURS_INVALID("TR-0006","durationHours must be 0 at minimum"),
  TR_DURATION_MINUTES_INVALID("TR-0007","durationMinutes must be 0 at minimum"),
  TR_DURATION_INVALID("TR-0008","At least one of durationHours and durationMinutes must be greater than 0"),
  TR_SEQUENCE_NUMBER_ALREADY_SET("TR-0011","sequencenumber already set on timereport"),
  TR_CLOSED_TIME_REPORT_REQ_ADMIN("TR-0012","closed time reports can only be saved by admins; reopen the period first."),
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
  TR_DURATION_EXCEEDS_ONE_DAY("TR-0030", "duration must not exceed 24 hours"),
  TR_DURATION_INVALID_FORMAT("TR-0031", "duration could not be interpreted"),
  TR_TICKET_REFERENCE_INVALID_LENGTH("TR-0032", "ticket reference is too long"),
  TR_SERIAL_DAYS_OUT_OF_RANGE("TR-0033", "number of serial days out of valid range"),
  TR_CSV_VALUE_FORMAT_INVALID("TR-0034", "a value of the uploaded CSV file matches none of the expected formats"),
  TR_CSV_LINE_NOT_READABLE("TR-0035", "a line of the uploaded CSV file could not be read"),
  TR_EMPLOYEE_CONTRACT_OTHER_EMPLOYEE("TR-0036", "a booking cannot move to another employee when it is edited"),
  TR_CSV_VALUE_TOO_LONG("TR-0037", "a value of the uploaded CSV file exceeds the maximum length"),
  TR_BOOKING_ORDER_NOT_NAMED("TR-0038", "a booking names neither an employee order id nor a suborder sign"),
  TR_BOOKING_EMPLOYEE_UNKNOWN("TR-0039", "no employee carries the sign of the booking"),
  TR_BOOKING_NO_CONTRACT("TR-0040", "no employee contract of the booking's employee is valid on the day of the booking"),
  TR_BOOKING_NO_EMPLOYEE_ORDER("TR-0041", "no employee order valid on the day matches the suborder sign of the booking"),
  TR_BOOKING_AMBIGUOUS_EMPLOYEE_ORDER("TR-0042", "several employee orders valid on the day match the suborder sign of the booking"),
  TR_BOOKING_OF_OTHER_EMPLOYEE("TR-0043", "the booking belongs to another employee than the selected contract"),
  TR_BOOKING_ORDER_CONTRADICTS_SIGN("TR-0044", "the employee order id and the suborder sign of the booking contradict each other"),
  TR_CSV_LINE_REJECTED("TR-0045", "a line of the uploaded CSV file names a booking that cannot be assigned"),
  TR_MOVE_ACCEPTED_REQ_ADMIN("TR-0046", "the range contains accepted time reports, which only admins may move"),
  TR_SUCCEEDED_CONTRACT_NOT_SELF("TR-0047", "own time reports of an ended contract cannot be changed once a later contract has been released"),

  RL_RELEASE_NOT_ALLOWED("RL-0001", "release not allowed"),
  RL_ACCEPT_NOT_ALLOWED("RL-0002", "accept not allowed"),
  RL_RELEASE_DATE_INVALID("RL-0003", "release date is null or outside contract validity range"),
  RL_RELEASE_DATE_BEFORE_ACCEPTANCE("RL-0004", "release date must not be before the acceptance date"),
  RL_ACCEPTANCE_DATE_INVALID("RL-0005", "acceptance date is null or outside contract validity range"),
  RL_ACCEPTANCE_DATE_AFTER_RELEASE("RL-0006", "acceptance date must not be after the release date"),
  RL_ACCEPTANCE_DATE_MOVED_BACKWARDS("RL-0007", "acceptance date must not move backwards"),
  RL_REVIEWED_PERIOD_CHANGED("RL-0008", "the period changed since it was reviewed"),
  RL_NOTHING_TO_RELEASE("RL-0009", "everything up to the release date has already been released"),
  RL_NOTHING_TO_ACCEPT("RL-0010", "everything up to the acceptance date has already been accepted"),
  RL_ACCEPTANCE_WITHOUT_RELEASE("RL-0011", "nothing has been released that could be accepted"),

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
  WD_DAY_LENGTH_TOO_LONG("WD-0014", "standby and working time together must not exceed 24 hours per day!"),
  WD_COMMITTED_REQ_PEOPLE_LEAD_OR_MANAGER("WD-0015", "in the released period only people leads and managers may change the working day"),
  WD_COMMITTED_NOT_SELF("WD-0016", "the own working day cannot be changed in the released period"),
  WD_CLOSED_REQ_ADMIN("WD-0017", "in the accepted period only admins may change the working day; reopen the period first"),
  WD_SUCCEEDED_CONTRACT_NOT_SELF("WD-0018", "the own working day of an ended contract cannot be changed once a later contract has been released"),

  ETL_INVALID_DATE_RANGE("ETL-0001", "etl definition executed with invalid date range"),
  ETL_RUN_ALREADY_RUNNING("ETL-0002", "an etl run is already running"),
  ETL_RUN_NOT_FOUND("ETL-0003", "etl run not found"),
  ETL_RUN_NOT_RUNNING("ETL-0004", "etl run is not running"),
  ETL_NO_EXECUTABLE_DEFINITION("ETL-0005", "no etl definition may be executed by this login"),
  ETL_DEFINITION_NOT_FOUND("ETL-0006", "etl definition not found"),
  ETL_RUN_EXECUTOR_BUSY("ETL-0007", "the etl execution thread is still occupied by an earlier run"),
  ETL_CYCLIC_DEPENDENCY("ETL-0008", "the etl definitions depend on each other in a cycle"),
  ETL_DEFINITIONS_NAME_OLD_SIGN("ETL-0009", "etl definitions name the old sign of a renamed order or suborder"),

  SE_USER_NOT_FOUND("SE-0001", "salat user not found for current login"),

  BU_BUDGET_NOT_FOUND("BU-0001", "order budget not found"),
  BU_ADJUSTMENT_NOT_FOUND("BU-0002", "order budget adjustment not found"),
  BU_PRICING_NOT_FOUND("BU-0003", "order pricing not found"),
  BU_PRICING_OVERLAP("BU-0006", "overlapping order pricing record exists"),
  BU_EMPLOYEE_COST_OVERLAP("BU-0007", "overlapping employee cost record exists for same name"),
  BU_EMPLOYEE_COST_ASSIGNMENT_OVERLAP("BU-0008", "overlapping employee cost assignment exists for same scope"),
  BU_EMPLOYEE_COST_NOT_FOUND("BU-0004", "employee cost not found"),
  BU_EMPLOYEE_COST_ASSIGNMENT_NOT_FOUND("BU-0005", "employee cost assignment not found"),
  BU_SUBORDER_NOT_IN_ORDER("BU-0009", "suborder sign does not belong to the given customer order"),
  // BU-0010 was BU_BUDGET_OVERLAP: overlapping plans of the same scope were forbidden while the
  // assignment of a booking was derived from (suborder, date) and would have been ambiguous. The
  // explicit assignment (#908) and the switched evaluation (#913) removed that reason, so the rule
  // is gone (#914). The number stays retired — codes are never reused or renumbered.
  BU_BUDGET_LEVEL_MIXED("BU-0011", "all plans of a customer order in force at the same time must sit on the same level"),
  // BU-0012 was BU_SUBORDER_NOT_FIRST_LEVEL: plans were confined to the first suborder level (#905)
  // because coverage was expressed through the booking's first level ancestor. Expressed as a
  // subtree instead, any level works and the confinement is gone (#1004) — what is left is the rule
  // that all plans in force at one time sit on the same level, which is BU-0011. The number stays
  // retired — codes are never reused or renumbered.
  BU_ORDER_NOT_AUTHORIZED("BU-0013", "not authorized to see budget data of this customer order"),
  BU_EMPLOYEE_COST_HAS_ASSIGNMENTS("BU-0014", "employee cost cannot be deleted while assignments reference its name"),
  BU_BUDGET_INACTIVE("BU-0015", "time reports cannot be assigned to an inactive order budget"),
  BU_TIMEREPORT_OUTSIDE_BUDGET_PERIOD("BU-0016", "the time report date lies outside the validity of the order budget"),
  BU_TIMEREPORT_NOT_IN_BUDGET_SCOPE("BU-0017", "the time report does not lie within the scope of the order budget"),
  BU_EMPLOYEE_COST_NAME_EXISTS("BU-0018", "a cost category of that name already exists"),
  // #958: budget records reference orders and cost categories by sign rather than by a foreign key.
  // An unknown sign resolves to nothing at all, and it does so silently, so it is rejected when
  // written instead of surfacing as a missing cost or an unused rate months later. BU-0019 did the
  // same for the employee until #968 moved that reference onto employee.id; it is not reused.
  // BU-0020 was BU_SUBORDER_SIGN_UNKNOWN (#958): cost assignments name their suborder by id since #1205
  BU_EMPLOYEE_COST_NAME_UNKNOWN("BU-0022", "no cost category exists with that name"),
  // #972: flat rates. There is deliberately no overlap code — flat rates add up by design, so two
  // definitions covering the same period are a legitimate case, not a conflict.
  BU_FLAT_RATE_NOT_FOUND("BU-0023", "order flat rate not found"),
  BU_FLAT_RATE_INSTALMENT_NOT_FOUND("BU-0024", "order flat rate instalment not found"),
  BU_FLAT_RATE_NOT_BILLED_IN_INSTALMENTS("BU-0025", "instalments only exist on a flat rate billed in instalments"),
  BU_FLAT_RATE_INSTALMENT_OUTSIDE_PERIOD("BU-0026", "the instalment date lies outside the validity of the flat rate"),
  BU_FLAT_RATE_AMOUNT_REQUIRED("BU-0027", "a flat rate billed once or monthly needs an amount"),
  // #1065: a rate or a flat rate may name the budget plan it applies to. A plan that cannot ever
  // meet the record is refused when written, not merely left out of the select — the pairing would
  // otherwise earn nothing and look complete while doing so (→ OrderBudgetBinding).
  BU_BUDGET_SCOPE_DISJOINT("BU-0028", "the budget plan belongs to another customer order or its scope does not intersect"),
  BU_BUDGET_PERIOD_DISJOINT("BU-0029", "the validity of the budget plan does not overlap the validity of the record"),
  BU_ORDER_HAS_BUDGET_REFERENCES("BU-0030", "budget plans, flat rates, customer rates or employee cost assignments still refer to the customer order"),
  BU_SUBORDER_HAS_BUDGET_REFERENCES("BU-0031", "budget plans, flat rates or employee cost assignments still refer to the suborder"),
  BU_PRICING_PATTERN_NOT_FOLLOWED("BU-0032", "the suborder pattern of a customer rate could not follow a renamed order or suborder"),
  BU_EMPLOYEE_COST_ASSIGNMENT_SCOPE_AMBIGUOUS("BU-0033", "an employee cost assignment is either for a suborder or for a customer order, not for both"),

  JI_REPLICATION_NOT_FOUND("JI-0001", "jira replication config not found"),
  JI_REPLICATION_NAME_REQUIRED("JI-0002", "a jira replication needs a name"),
  JI_REPLICATION_SCOPE_REQUIRED("JI-0003", "a jira replication needs a scope"),
  JI_REPLICATION_BASE_URL_REQUIRED("JI-0004", "a jira replication needs a base url"),
  JI_REPLICATION_BASE_URL_INVALID("JI-0005", "the base url must start with http:// or https://"),
  JI_REPLICATION_USERNAME_REQUIRED("JI-0006", "a jira replication needs a username"),
  JI_REPLICATION_PASSWORD_REQUIRED("JI-0007", "a new jira replication needs a password"),
  JI_REPLICATION_JQL_REQUIRED("JI-0008", "a jira replication needs a jql query"),
  JI_REPLICATION_PAGE_SIZE_INVALID("JI-0009", "the page size must be a positive number"),
  JI_REPLICATION_SCOPE_NOT_FOUND("JI-0010", "the scope of a jira replication must be an existing customer order or suborder"),
  JI_REPLICATION_WORKLOG_SCOPE_OVERLAP("JI-0011", "another replication writing worklogs to the same jira instance already covers this scope"),
  JI_REPLICATION_RUN_ALREADY_RUNNING("JI-0012", "this jira replication is already running"),
  JI_REPLICATION_RUN_NOT_FOUND("JI-0013", "jira replication run not found"),
  JI_REPLICATION_RUN_NOT_RUNNING("JI-0014", "jira replication run is not running"),
  JI_REPLICATION_RUN_EXECUTOR_BUSY("JI-0015", "every thread for manually started jira replications is occupied"),
  JI_ORDER_HAS_REPLICATIONS("JI-0016", "jira replications still refer to the customer order"),
  JI_SUBORDER_HAS_REPLICATIONS("JI-0017", "jira replications still refer to the suborder"),

  RP_REPORT_NOT_FOUND("RP-0001", "the report was not found"),
  RP_REPORT_NAME_AMBIGUOUS("RP-0002", "the report name matches more than one report"),
  RP_REPORT_PARAMETERS_MISSING("RP-0003", "the report needs parameters that were not given"),
  RP_REPORT_PARAMETER_INVALID("RP-0004", "a report parameter cannot be interpreted as its type"),
  RP_REPORT_EXECUTION_FAILED("RP-0005", "the report could not be executed"),
  RP_REPORT_ID_NOT_FOUND("RP-0006", "there is no report with this id"),
  RP_REPORT_ID_INVALID("RP-0007", "the report id is not a number"),
  RP_REPORT_NOT_SPECIFIED("RP-0008", "the report must be given by reportId or by report (its name)"),
  RP_DEFINITIONS_NAME_OLD_SIGN("RP-0009", "report definitions name the old sign of a renamed order or suborder"),
  RP_REPORT_NAME_TAKEN("RP-0010", "another report definition already has this name"),

  XX_UNHANDLED_SERVLET_EXCEPTION("XX-0001", "Unhandled servlet exception"),
  XX_DATA_MISSING("XX-0002", "Required data missing"),
  XX_CONCURRENT_MODIFICATION("XX-0003", "the data was changed concurrently"),
  XX_DUPLICATE_KEY("XX-0004", "a record with the same key already exists"),
  ;

  private final String code;
  private final String message;

  @Override
  public String toString() {
    return code + ": " + message;
  }

}
