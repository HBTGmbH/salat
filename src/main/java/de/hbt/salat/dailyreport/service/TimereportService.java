package de.hbt.salat.dailyreport.service;

import static java.lang.Boolean.TRUE;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingLong;
import static java.util.stream.Collectors.toMap;
import static de.hbt.salat.common.GlobalConstants.BREAK_MINUTES_AFTER_NINE_HOURS;
import static de.hbt.salat.common.GlobalConstants.BREAK_MINUTES_AFTER_SIX_HOURS;
import static de.hbt.salat.common.GlobalConstants.COMMENT_MAX_LENGTH;
import static de.hbt.salat.common.GlobalConstants.DEBITHOURS_UNIT_MONTH;
import static de.hbt.salat.common.GlobalConstants.DEBITHOURS_UNIT_TOTALTIME;
import static de.hbt.salat.common.GlobalConstants.DEBITHOURS_UNIT_YEAR;
import static de.hbt.salat.common.GlobalConstants.MAX_SERIAL_BOOKING_DAYS;
import static de.hbt.salat.common.GlobalConstants.MINUTES_PER_DAY;
import static de.hbt.salat.common.GlobalConstants.NINE_HOURS_IN_MINUTES;
import static de.hbt.salat.common.GlobalConstants.SIX_HOURS_IN_MINUTES;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static de.hbt.salat.common.LocalDateRange.FINIT_FROM_BOUNDARY;
import static de.hbt.salat.common.LocalDateRange.FINIT_UNTIL_BOUNDARY;
import static de.hbt.salat.common.exception.ErrorCode.AA_NEEDS_MANAGER;
import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static de.hbt.salat.common.exception.ErrorCode.TR_ABSENCE_DAY_EXCEEDS_TARGET;
import static de.hbt.salat.common.exception.ErrorCode.TR_ABSENCE_DAY_WITHOUT_TARGET;
import static de.hbt.salat.common.exception.ErrorCode.TR_DURATION_EXCEEDS_ONE_DAY;
import static de.hbt.salat.common.exception.ErrorCode.TR_DURATION_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_INVALID_REF_DATE;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_OTHER_EMPLOYEE;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_INVALID_REF_DATE;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_OF_OTHER_CONTRACT;
import static de.hbt.salat.common.exception.ErrorCode.TR_MONTH_BUDGET_EXCEEDED;
import static de.hbt.salat.common.exception.ErrorCode.TR_REFERENCE_DAY_NULL;
import static de.hbt.salat.common.exception.ErrorCode.TR_SEQUENCE_NUMBER_ALREADY_SET;
import static de.hbt.salat.common.exception.ErrorCode.TR_SERIAL_DAYS_OUT_OF_RANGE;
import static de.hbt.salat.common.exception.ErrorCode.TR_SUBORDER_COMMENT_MANDATORY;
import static de.hbt.salat.common.exception.ErrorCode.TR_SURCHARGE_HOLIDAY_ON_NON_HOLIDAY;
import static de.hbt.salat.common.exception.ErrorCode.TR_SURCHARGE_NOT_OFFERED;
import static de.hbt.salat.common.exception.ErrorCode.TR_SURCHARGE_SATURDAY_ON_OTHER_DAY;
import static de.hbt.salat.common.exception.ErrorCode.TR_SURCHARGE_SUNDAY_ON_OTHER_DAY;
import static de.hbt.salat.common.exception.ErrorCode.TR_TASK_DESCRIPTION_INVALID_LENGTH;
import static de.hbt.salat.common.exception.ErrorCode.TR_TICKET_REFERENCES_EXCEED_LIMIT;
import static de.hbt.salat.common.exception.ErrorCode.TR_TICKET_REFERENCES_NOT_ALLOWED;
import static de.hbt.salat.common.exception.ErrorCode.TR_TIMEREPORTS_EXIST_CANNOT_DELETE_OR_UPDATE_EMPLOYEE_ORDER;
import static de.hbt.salat.common.exception.ErrorCode.TR_TIME_REPORT_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.TR_TOTAL_BUDGET_EXCEEDED;
import static de.hbt.salat.common.exception.ErrorCode.TR_WORKING_DAY_NOT_WORKED;
import static de.hbt.salat.common.exception.ErrorCode.TR_WORKING_DAY_START_NULL;
import static de.hbt.salat.common.exception.ErrorCode.XX_CONCURRENT_MODIFICATION;
import static de.hbt.salat.common.exception.ErrorCode.TR_YEAR_BUDGET_EXCEEDED;
import static de.hbt.salat.common.exception.ErrorCode.TR_YEAR_OUT_OF_RANGE;
import static de.hbt.salat.common.exception.ErrorCode.WD_BEGIN_TIME_MISSING;
import static de.hbt.salat.common.exception.ErrorCode.WD_BREAK_TOO_SHORT_6;
import static de.hbt.salat.common.exception.ErrorCode.WD_BREAK_TOO_SHORT_9;
import static de.hbt.salat.common.exception.ServiceFeedbackMessage.error;
import static de.hbt.salat.common.util.DateUtils.getFirstDay;
import static de.hbt.salat.common.util.DateUtils.getLastDay;
import static de.hbt.salat.common.util.DateUtils.getYear;
import static de.hbt.salat.common.util.DateUtils.getYearMonth;
import static de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.util.BusinessRuleCheckUtils;
import de.hbt.salat.common.util.DataValidationUtils;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.common.util.DurationUtils;
import de.hbt.salat.common.util.TicketReferences;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.AccountBooking;
import de.hbt.salat.dailyreport.domain.PreviousBooking;
import de.hbt.salat.dailyreport.domain.Publicholiday;
import de.hbt.salat.dailyreport.domain.RecentBooking;
import de.hbt.salat.dailyreport.domain.Referenceday;
import de.hbt.salat.dailyreport.domain.ReportPeriod;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.event.TimereportsCreatedOrUpdatedEvent;
import de.hbt.salat.dailyreport.event.TimereportsDeletedEvent;
import de.hbt.salat.dailyreport.event.TimereportsDeletedEvent.TimereportDeleteId;
import de.hbt.salat.dailyreport.persistence.PublicholidayDAO;
import de.hbt.salat.dailyreport.persistence.ReferencedayRepository;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.TimereportRepository;
import de.hbt.salat.dailyreport.persistence.WorkingdayDAO;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeecontractDAO;
import de.hbt.salat.jira.command.GetTicketWorklogSumsCommandEvent;
import de.hbt.salat.order.command.GetTimereportMinutesCommandEvent;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SurchargeKind;
import de.hbt.salat.order.domain.TicketReferencePolicy;
import de.hbt.salat.order.event.EmployeeorderConflictResolutionEvent;
import de.hbt.salat.order.event.EmployeeorderDeleteEvent;
import de.hbt.salat.order.event.EmployeeorderUpdateEvent;
import de.hbt.salat.order.persistence.EmployeeorderDAO;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class TimereportService {

  private final ApplicationEventPublisher eventPublisher;
  private final EmployeecontractDAO employeecontractDAO;
  private final ReferencedayRepository referencedayRepository;
  private final EmployeeorderDAO employeeorderDAO;
  private final TimereportDAO timereportDAO;
  private final TimereportRepository timereportRepository;
  private final PublicholidayDAO publicholidayDAO;
  private final WorkingdayDAO workingdayDAO;
  private final AuthorizedUser authorizedUser;
  private final TimereportAuthorization timereportAuthorization;

  public TimereportDTO getTimereportById(long id) {
    return timereportDAO.getTimereportById(id);
  }

  /**
   * Whether the current user may change or delete this booking — the status rule saving and deleting apply
   * ({@link TimereportAuthorization#isWriteAllowed}), so the booking form offers "Löschen" only where it would succeed
   * (#1192). For both the rule is the same. {@code false} for a booking that does not exist.
   */
  @Transactional(readOnly = true)
  public boolean isWriteAllowed(long timereportId) {
    return timereportRepository.findById(timereportId)
        .map(timereport -> timereportAuthorization.isWriteAllowed(timereport.getEmployeecontract(), timereport.getStatus()))
        .orElse(false);
  }

  public List<Long> createTimereports(long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      boolean trainingFlag, Duration duration, int numberOfSerialDays) throws ErrorCodeException {
    return createTimereports(employeeContractId, employeeOrderId, referenceDay, taskDescription, null, trainingFlag, duration,
        numberOfSerialDays);
  }

  /**
   * @param ticketReferences stored under the rule of {@link TicketReferences}; how many the suborder
   *                         allows is checked on saving (#1326)
   */
  public List<Long> createTimereports(long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      List<String> ticketReferences, boolean trainingFlag, Duration duration, int numberOfSerialDays)
      throws ErrorCodeException {
    return createTimereports(employeeContractId, employeeOrderId, referenceDay, taskDescription, ticketReferences, trainingFlag,
        SurchargeChoice.NONE, duration, numberOfSerialDays);
  }

  /**
   * @param surcharge the time surcharge the bookings are flagged with (#1469); see {@link SurchargeChoice}
   *                  for where the rate comes from
   * @return the ids of the bookings created, one per serial day in their order — what a caller needs to answer
   *         with the new booking or to remember it for a repetition (#1532)
   */
  public List<Long> createTimereports(long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      List<String> ticketReferences, boolean trainingFlag, SurchargeChoice surcharge, Duration duration,
      int numberOfSerialDays) throws ErrorCodeException {

    // the form offers 1..MAX_SERIAL_BOOKING_DAYS; a hand-crafted POST must not ask for more (#826)
    if (numberOfSerialDays < 1 || numberOfSerialDays > MAX_SERIAL_BOOKING_DAYS) {
      throw new InvalidDataException(TR_SERIAL_DAYS_OUT_OF_RANGE);
    }

    Timereport timereportTemplate = new Timereport();
    validateParametersAndFillTimereport(employeeContractId, employeeOrderId, referenceDay, taskDescription, trainingFlag, duration,
        timereportTemplate);
    // set on the template, so getTwin() carries them to every serial day
    timereportTemplate.setTicketReferences(TicketReferences.normalize(ticketReferences));
    applySurchargeChoice(timereportTemplate, surcharge);

    // create a timereport for every serial day requested - in most cases this is 1
    List<Timereport> timereportsToSave = new ArrayList<>();
    for (int i = 0; i < numberOfSerialDays; i++) {
      Timereport timereport = timereportTemplate.getTwin();
      setSequencenumber(timereport);
      setStatus(timereport);
      timereportsToSave.add(timereport);
      // increment to next workable day
      Referenceday nextWorkableDay = getNextWorkableDay(timereportTemplate.getReferenceday());
      timereportTemplate.setReferenceday(nextWorkableDay);
    }

    checkAndSaveTimereports(timereportsToSave);
    return timereportsToSave.stream().map(Timereport::getId).toList();
  }

  /**
   * The time surcharges a booking on {@code day} may carry besides the night (#1469): holiday on a
   * public holiday, Saturday on a Saturday, Sunday on a Sunday, in this order - the first one offered is
   * the one preselected ({@link #getPresetSurchargeKind}). Saturday and Sunday have rates of their own.
   * Saving checks the same rule ({@link #validateSurchargeDays}).
   */
  @Transactional(readOnly = true)
  public List<SurchargeKind> getSurchargeDayKinds(LocalDate day) {
    var kinds = new ArrayList<SurchargeKind>();
    if (publicholidayDAO.getPublicHoliday(day).isPresent()) {
      kinds.add(SurchargeKind.HOLIDAY);
    }
    switch (day.getDayOfWeek()) {
      case SATURDAY -> kinds.add(SurchargeKind.SATURDAY);
      case SUNDAY -> kinds.add(SurchargeKind.SUNDAY);
      default -> { }
    }
    return kinds;
  }

  /**
   * The kind a new booking on {@code day} is flagged with until somebody chooses (#1469): the first of
   * {@link #getSurchargeDayKinds} that the suborder offers, holiday before Saturday or Sunday. The night
   * is never preselected, it is for the person to set. {@code null} where the day has none on offer.
   */
  @Transactional(readOnly = true)
  public SurchargeKind getPresetSurchargeKind(LocalDate day, Set<SurchargeKind> offered) {
    return getSurchargeDayKinds(day).stream().filter(offered::contains).findFirst().orElse(null);
  }

  /**
   * Refuses a booking on this day before anything is prepared for it, with the refusal saving would
   * give. The booking form seeds the working day of every serial day first, each in a transaction of
   * its own (#1111); on a released or accepted day that seeding is refused as well, but its message
   * speaks of the working day instead of the booking somebody tried to create (#1164).
   */
  @Transactional(readOnly = true)
  public void checkCreationAllowed(long employeeContractId, LocalDate referenceDay) {
    Employeecontract employeecontract = employeecontractDAO.getEmployeecontractById(employeeContractId);
    DataValidationUtils.notNull(employeecontract, TR_EMPLOYEE_CONTRACT_NOT_FOUND);
    timereportAuthorization.writeDenialOn(employeecontract, referenceDay).ifPresent(denial -> {
      throw new AuthorizationException(denial);
    });
  }

  public void updateTimereport(long timereportId, long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      boolean trainingFlag, Duration duration) throws ErrorCodeException {
    updateTimereport(timereportId, employeeContractId, employeeOrderId, referenceDay, taskDescription, trainingFlag, duration, false);
  }

  /**
   * Leaves the stored ticket references untouched. Callers that do not know about them — moving,
   * shifting, importing — must not silently drop them (#982).
   */
  public void updateTimereport(long timereportId, long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      boolean trainingFlag, Duration duration, boolean force) throws ErrorCodeException {
    updateTimereport(timereportId, employeeContractId, employeeOrderId, referenceDay, taskDescription, trainingFlag, duration,
        force, GivenFields.NONE);
  }

  /**
   * Writes the ticket references as given — an empty list clears them, which is what an emptied form
   * field means.
   */
  public void updateTimereport(long timereportId, long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      List<String> ticketReferences, boolean trainingFlag, Duration duration) throws ErrorCodeException {
    updateTimereport(timereportId, employeeContractId, employeeOrderId, referenceDay, taskDescription, trainingFlag, duration,
        false, GivenFields.ticketReferences(ticketReferences));
  }

  /**
   * The booking form: writes the ticket references and the time surcharge flag as given (#1469). The
   * stored rate stays as long as the kind and the suborder do, so that a later change of the rate on
   * the order does not reach an existing booking.
   */
  public void updateTimereport(long timereportId, long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      List<String> ticketReferences, boolean trainingFlag, SurchargeKind surchargeKind, Duration duration)
      throws ErrorCodeException {
    updateTimereport(timereportId, employeeContractId, employeeOrderId, referenceDay, taskDescription, trainingFlag, duration,
        false, GivenFields.form(ticketReferences, surchargeKind));
  }

  /**
   * The booking form's variant for a caller that read the booking earlier and must not overwrite a change made since
   * (#1532): {@code expectedVersion} is the {@link TimereportDTO#getVersion() version} it read. A booking changed in
   * between is refused with {@code XX-0003}, the answer a concurrent change within one transaction gets as well.
   */
  public void updateTimereport(long timereportId, int expectedVersion, long employeeContractId, long employeeOrderId,
      LocalDate referenceDay, String taskDescription, List<String> ticketReferences, boolean trainingFlag,
      SurchargeKind surchargeKind, Duration duration) throws ErrorCodeException {
    updateTimereport(timereportId, employeeContractId, employeeOrderId, referenceDay, taskDescription, trainingFlag, duration,
        false, GivenFields.form(ticketReferences, surchargeKind).expecting(expectedVersion));
  }

  /**
   * What a caller of {@link #updateTimereport} says beyond the fields every caller knows: the ticket references
   * (#982) and the time surcharge flag (#1469). A field not given keeps what the booking has — moving,
   * shifting and the inline edit know neither.
   */
  private record GivenFields(boolean ticketReferencesGiven, List<String> ticketReferences,
                             boolean surchargeKindGiven, SurchargeKind surchargeKind, Integer expectedVersion) {

    static final GivenFields NONE = new GivenFields(false, null, false, null, null);

    /** An empty or missing list clears the references, which is what an emptied form field means. */
    static GivenFields ticketReferences(List<String> ticketReferences) {
      return new GivenFields(true, ticketReferences == null ? List.of() : ticketReferences, false, null, null);
    }

    static GivenFields form(List<String> ticketReferences, SurchargeKind surchargeKind) {
      return new GivenFields(true, ticketReferences == null ? List.of() : ticketReferences, true, surchargeKind, null);
    }

    GivenFields expecting(int version) {
      return new GivenFields(ticketReferencesGiven, ticketReferences, surchargeKindGiven, surchargeKind, version);
    }
  }

  private void updateTimereport(long timereportId, long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      boolean trainingFlag, Duration duration, boolean force, GivenFields given) throws ErrorCodeException {
    if (force && !authorizedUser.isManager()) {
      throw new AuthorizationException(AA_NEEDS_MANAGER);
    }
    Timereport timereport = timereportRepository.findById(timereportId).orElse(null);
    DataValidationUtils.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    if (!force) {
      // the booking as it stands must be writable, not only the one it becomes (#1125)
      timereportAuthorization.checkAuthorized(Collections.singletonList(timereport), AccessLevel.WRITE);
    }
    // after the right to write: who may not write gets that answer, not a conflict that invites a retry
    if (given.expectedVersion() != null && !given.expectedVersion().equals(timereport.getUpdatecounter())) {
      throw new BusinessRuleException(XX_CONCURRENT_MODIFICATION);
    }
    LocalDate previousDate = timereport.getReferenceday().getRefdate();
    Employeecontract previousContract = timereport.getEmployeecontract();
    // taken before the booking is filled with the new values: the limit of a day with an absence compares
    // the day before and after the change (#397)
    var previousState = StoredBooking.of(timereport);
    validateParametersAndFillTimereport(employeeContractId, employeeOrderId, referenceDay, taskDescription, trainingFlag, duration,
        timereport);
    // across a contract boundary a booking changes its contract, never its person (#1128)
    BusinessRuleCheckUtils.isTrue(
        Objects.equals(previousContract.getEmployee().getId(), timereport.getEmployeecontract().getEmployee().getId()),
        TR_EMPLOYEE_CONTRACT_OTHER_EMPLOYEE);
    if (given.ticketReferencesGiven()) {
      timereport.setTicketReferences(TicketReferences.normalize(given.ticketReferences()));
    }
    applySurcharge(timereport, given.surchargeKindGiven() ? given.surchargeKind() : previousState.surchargeKind(),
        previousState);
    boolean dayChanged = !previousDate.equals(referenceDay);
    boolean contractChanged = !Objects.equals(previousContract.getId(), employeeContractId);
    if (dayChanged || contractChanged) {
      // a booking moved to another day or contract is treated as if it were created there (#1125, #1128)
      setStatus(timereport);
    }
    Map<Long, LocalDate> previousReferencedays = dayChanged ? Map.of(timereportId, previousDate) : Map.of();
    Map<Long, Long> previousEmployeecontractIds = contractChanged ? Map.of(timereportId, previousContract.getId()) : Map.of();
    checkAndSaveTimereports(Collections.singletonList(timereport), force, previousReferencedays, previousEmployeecontractIds,
        List.of(previousState));
  }

  /**
   * The contract an edited booking belongs to on {@code referenceDay}: the one of its own person valid
   * on that day (#1128). A remembered selection of another person has no say in it. Where the person
   * has no contract on that day, the booking keeps its own, and saving reports that it is not valid
   * there. {@code -1} for a booking the user cannot see, as for an unknown one.
   */
  @Transactional(readOnly = true)
  public long getEmployeecontractIdForUpdate(long timereportId, LocalDate referenceDay) {
    TimereportDTO timereport = timereportDAO.getTimereportById(timereportId);
    if (timereport == null) {
      return -1;
    }
    if (referenceDay == null) {
      return timereport.getEmployeecontractId();
    }
    Employeecontract contract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(timereport.getEmployeeId(), referenceDay);
    return contract != null ? contract.getId() : timereport.getEmployeecontractId();
  }

  /**
   * How many ticket references this booking may carry, as its suborder sets it after inheritance
   * (#1326); {@code null} for a booking that does not exist. The inline edit of the daily view asks
   * before it proposes keys from the comment.
   */
  @Transactional(readOnly = true)
  public TicketReferencePolicy getTicketReferencePolicy(long timereportId) {
    return timereportRepository.findById(timereportId)
        .map(timereport -> timereport.getSuborder().getEffectiveTicketReferencePolicy())
        .orElse(null);
  }

  /**
   * shifts a timereport by days
   */
  public void shiftDays(long timereportId, int amountDays) throws ErrorCodeException {
    Timereport timereport = timereportRepository.findById(timereportId).orElse(null);
    DataValidationUtils.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    Referenceday referenceday = timereport.getReferenceday();
    LocalDate shiftedDate = DateUtils.addDays(referenceday.getRefdate(), amountDays);
    updateTimereport(timereport.getId(),
        timereport.getEmployeecontract().getId(),
        timereport.getEmployeeorder().getId(),
        shiftedDate,
        timereport.getTaskdescription(),
        timereport.isTraining(),
        timereport.getDuration());
  }

  public void deleteTimereportById(long timereportId) throws ErrorCodeException {
    Timereport timereport = timereportRepository.findById(timereportId).orElse(null);
    DataValidationUtils.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    timereportAuthorization.checkAuthorized(Collections.singletonList(timereport), AccessLevel.DELETE);
    var deleteId = new TimereportDeleteId(
        timereportId,
        timereport.getEmployeeorder().getId(),
        timereport.getReferenceday().getRefdate()
    );
    var event = new TimereportsDeletedEvent(List.of(deleteId));
    timereportRepository.deleteById(timereportId);
    eventPublisher.publishEvent(event);
  }

  public void deleteTimeReports(LocalDate date, long employeeOrderId) {
    var timeReports = timereportRepository.findAllByEmployeeorderIdAndReferencedayRefdate(employeeOrderId, date);
    timereportAuthorization.checkAuthorized(timeReports, AccessLevel.DELETE);
    deleteTimereports(timeReports);
  }

  public void deleteTimereportsById(List<Long> timereportIds) throws ErrorCodeException {
    List<Timereport> timereports = timereportIds.stream().map(timereportRepository::findById)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
    deleteTimereports(timereports);
  }

  private void deleteTimereports(List<Timereport> timereports) {
    timereportAuthorization.checkAuthorized(timereports, AccessLevel.DELETE);
    var deleteIds = timereports.stream()
        .map(t -> new TimereportDeleteId(t.getId(), t.getEmployeeorder().getId(), t.getReferenceday().getRefdate()))
        .toList();
    timereportRepository.deleteAll(timereports);
    eventPublisher.publishEvent(new TimereportsDeletedEvent(deleteIds));
  }

  private void checkAndSaveTimereports(List<Timereport> timereports) {
    checkAndSaveTimereports(timereports, false, Map.of(), Map.of(), List.of());
  }

  /**
   * @param previousStates the bookings among {@code timereports} that exist already, as they stood
   *                       before this change — what the limit of a day with an absence compares with
   */
  private void checkAndSaveTimereports(List<Timereport> timereports, boolean force, Map<Long, LocalDate> previousReferencedays,
      Map<Long, Long> previousEmployeecontractIds, List<StoredBooking> previousStates) {
    timereports.forEach(t -> log.debug("checking Timereport {}", t.getTimeReportAsString()));

    if (!force) {
      timereportAuthorization.checkAuthorized(timereports, AccessLevel.WRITE);
    }
    validateWorkingDayBusinessRules(timereports);
    validateTimeReportingBusinessRules(timereports);
    validateContractBusinessRules(timereports);
    validateOrderBusinessRules(timereports);
    validateTicketReferences(timereports);
    validateSurchargeDays(timereports, previousStates);
    validateAbsenceDayLimit(timereports, previousStates);
    validateEmployeeorderBudget(timereports);

    timereports.forEach(t -> {
      log.debug("Saving Timereport {}", t.getTimeReportAsString());
      timereportRepository.save(t);
    });

    var ids = timereports.stream().map(Timereport::getId).toList();
    eventPublisher.publishEvent(new TimereportsCreatedOrUpdatedEvent(ids, previousReferencedays, previousEmployeecontractIds));
  }

  private void validateParametersAndFillTimereport(long employeeContractId, long employeeOrderId, LocalDate referenceDay, String taskDescription,
      boolean trainingFlag, Duration duration,
      Timereport timereport) {
    Employeecontract employeecontract = employeecontractDAO.getEmployeecontractById(employeeContractId);
    DataValidationUtils.notNull(employeecontract, TR_EMPLOYEE_CONTRACT_NOT_FOUND);
    Employeeorder employeeorder = employeeorderDAO.getEmployeeorderById(employeeOrderId);
    DataValidationUtils.notNull(employeeorder, TR_EMPLOYEE_ORDER_NOT_FOUND);
    // the contract of a booking is that of its employee order (#1210): an employee order of another
    // contract - from a favourite or a previous booking - would book for that contract instead
    if (!Objects.equals(employeeorder.getEmployeecontract().getId(), employeecontract.getId())) {
      throw new InvalidDataException(TR_EMPLOYEE_ORDER_OF_OTHER_CONTRACT,
          employeeorder.getSuborder().getCompleteOrderSign(),
          contractLabel(employeeorder.getEmployeecontract()),
          contractLabel(employeecontract));
    }
    DataValidationUtils.notNull(referenceDay, TR_REFERENCE_DAY_NULL);
    Referenceday referenceday = getOrAddReferenceday(referenceDay);
    DataValidationUtils.lengthIsInRange(taskDescription, 0, COMMENT_MAX_LENGTH, TR_TASK_DESCRIPTION_INVALID_LENGTH);
    // hours and minutes apart exist only where a value comes in from outside (#1400), and are checked there
    DataValidationUtils.notNull(duration, TR_DURATION_INVALID);
    DataValidationUtils.isTrue(!duration.isNegative(), TR_DURATION_INVALID);
    // a booking cannot be longer than the day it belongs to (#825). Enforced here so that every
    // caller is covered - the booking form, the inline edit of the daily view and the REST API.
    DataValidationUtils.isTrue(duration.compareTo(Duration.ofMinutes(MINUTES_PER_DAY)) <= 0, TR_DURATION_EXCEEDS_ONE_DAY);

    timereport.setEmployeeorder(employeeorder);
    timereport.setReferenceday(referenceday);
    timereport.setTaskdescription(taskDescription.trim());
    timereport.setTraining(trainingFlag);
    timereport.setDuration(duration);
  }

  /**
   * Flags a new booking as {@code choice} says (#1469): the kind of the day for a one-click booking, the rate of
   * the stored booking it replaces for an import, else as {@link #applySurcharge}.
   */
  private void applySurchargeChoice(Timereport timereport, SurchargeChoice choice) {
    var kind = choice.dayPreset()
        ? getPresetSurchargeKind(timereport.getReferenceday().getRefdate(), offeredSurchargeKinds(timereport.getSuborder()))
        : choice.kind();
    if (kind != null && choice.storedPercent() != null) {
      timereport.setSurcharge(kind, choice.storedPercent());
      return;
    }
    applySurcharge(timereport, kind, null);
  }

  private static Set<SurchargeKind> offeredSurchargeKinds(Suborder suborder) {
    return Arrays.stream(SurchargeKind.values())
        .filter(kind -> suborder.getOfferedSurchargeRate(kind) != null)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(SurchargeKind.class)));
  }

  /**
   * Flags {@code timereport} with {@code kind} (#1469): at the stored rate where the booking keeps it,
   * else at the rate its suborder offers now — refused where the suborder offers none of that kind.
   * Whether the day fits the kind is checked with the other rules on saving.
   */
  private static void applySurcharge(Timereport timereport, SurchargeKind kind, StoredBooking previous) {
    if (kind == null) {
      timereport.setSurcharge(null, null);
      return;
    }
    if (previous != null && previous.keepsSurchargeRate(kind, timereport.getSuborder())) {
      timereport.setSurcharge(kind, previous.surchargePercent());
      return;
    }
    var rate = timereport.getSuborder().getOfferedSurchargeRate(kind);
    if (rate == null) {
      throw new BusinessRuleException(TR_SURCHARGE_NOT_OFFERED, timereport.getSuborder().getCompleteOrderSign());
    }
    timereport.setSurcharge(kind, rate.percent());
  }

  private static String contractLabel(Employeecontract employeecontract) {
    return employeecontract.getEmployee().getSign() + " " + employeecontract.getTimeString().strip();
  }

  /**
   * Gets the referenceday for the given date. In case the
   * referenceday does not exists, create a new one.
   */
  public Referenceday getOrAddReferenceday(LocalDate refDate) {
    return referencedayRepository
        .findByRefdate(refDate)
        .orElseGet(() -> addReferenceday(refDate));
  }

  /**
   * Adds a referenceday to database at the time when it is first referenced in a new timereport.
   */
  public Referenceday addReferenceday(LocalDate date) {
    Referenceday rd = new Referenceday();
    rd.setRefdate(date);
    rd.applyCalendar(publicholidayDAO.getPublicHoliday(date).map(Publicholiday::getName).orElse(null));
    referencedayRepository.save(rd);
    return rd;
  }

  boolean needsWorkingHoursLawValidation(long employeeContractId) {
    Employeecontract contract = employeecontractDAO.getEmployeecontractById(employeeContractId);
    return contract != null && !contract.getEmployee().isRestricted();
  }

  private void setSequencenumber(Timereport timereport) {
    BusinessRuleCheckUtils.isTrue(timereport.getSequencenumber() == 0, TR_SEQUENCE_NUMBER_ALREADY_SET);
    List<TimereportDTO> existingTimereports = timereportDAO.getTimereportsByDateAndEmployeeContractId(
        timereport.getEmployeecontract().getId(),
        timereport.getReferenceday().getRefdate()
    );
    int maxSequencenumber = existingTimereports
        .stream()
        .map(TimereportDTO::getSequencenumber)
        .max(Integer::compareTo)
        .orElse(0);
    timereport.setSequencenumber(maxSequencenumber + 1);
  }

  /**
   * The status the day and the contract give the booking — on creation, and when a change moves it to
   * another day (#1125) or another contract (#1128). Who released or accepted a moved booking on its
   * old day stays only where the new status still says so, as {@code ReleaseService.reopenTimereport}
   * drops both when it reopens a booking. A new booking carries neither.
   */
  private void setStatus(Timereport timereport) {
    timereport.setStatus(ReportPeriod.statusOn(timereport.getEmployeecontract(), timereport.getReferenceday().getRefdate()));

    if (!TIMEREPORT_STATUS_CLOSED.equals(timereport.getStatus())) {
      timereport.setAcceptedby(null);
      timereport.setAccepted(null);
    }
    if (TIMEREPORT_STATUS_OPEN.equals(timereport.getStatus())) {
      timereport.setReleasedby(null);
      timereport.setReleased(null);
    }
  }

  @Transactional(readOnly = true)
  public List<LocalDate> getWorkableSerialDates(LocalDate start, int count) {
    // same bound as createTimereports: callers seed a working day per date, so an oversized count
    // must be rejected here as well and not only when the bookings are saved (#826)
    if (count < 1 || count > MAX_SERIAL_BOOKING_DAYS) {
      throw new InvalidDataException(TR_SERIAL_DAYS_OUT_OF_RANGE);
    }
    List<LocalDate> dates = new ArrayList<>();
    LocalDate current = start;
    for (int i = 0; i < count; i++) {
      dates.add(current);
      if (i < count - 1) {
        LocalDate next = current;
        do {
          next = DateUtils.addDays(next, 1);
        } while (!DateUtils.isWeekday(next) || publicholidayDAO.getPublicHoliday(next).isPresent());
        current = next;
      }
    }
    return dates;
  }

  private Referenceday getNextWorkableDay(Referenceday referenceday) {
    Referenceday nextWorkableDay = null;
    LocalDate day = referenceday.getRefdate();
    do {
      LocalDate nextDay = DateUtils.addDays(day, 1);
      if(DateUtils.isWeekday(nextDay)) {
        Optional<Publicholiday> publicHoliday = publicholidayDAO.getPublicHoliday(nextDay);
        if(publicHoliday.isEmpty()) {
          // we have found a weekday that is not a public holiday, hooray!
          nextWorkableDay = getOrAddReferenceday(nextDay);
        }
      }
      day = nextDay; // prepare next iteration
    } while(nextWorkableDay == null);
    return nextWorkableDay;
  }

  /**
   * On a day with an absence — vacation, sickness or anything else booked on an order of type
   * {@link OrderType#KRANK_URLAUB_ABWESEND} — all bookings of the day together must not exceed the target of
   * that day (#397). Otherwise an absence would earn overtime: the day off counts with its full working
   * time, and whatever is booked next to it comes on top. A day without an absence knows no such limit.
   *
   * <p>The target is the one the booking form proposes the rest of for an absence (#1214,
   * {@link DailyService#getRemainingDayTarget}), computed by {@link WorkingTimeTarget}: the daily working time
   * of the contract on a weekday within its validity that is no public holiday, nothing otherwise. Standby
   * is no working time (#463) and counts neither here nor there. So the proposal is exactly what this check
   * still lets through. Time surcharges (#1469) do not count either: the limit is about the time worked and
   * taken off, and a credit for a night or a Sunday is neither.
   *
   * <p>Checked on every save, whichever way the booking comes — the form, the inline edit, a favourite, a
   * previous booking, serial days, shifting and moving, the CSV import and the REST API all come through
   * {@link #checkAndSaveTimereports} — and from either side: an absence on a day with working time as much
   * as working time on a day with an absence.
   *
   * <p>Days that broke the rule before it existed stay as they are. Changing one of their bookings is
   * refused only where the change makes the excess larger: shortening a booking, correcting its comment
   * or moving it away has to remain possible, and none of that is a reason to reject.
   */
  private void validateAbsenceDayLimit(List<Timereport> timereports, List<StoredBooking> previousStates) {
    var savedIds = timereports.stream().map(Timereport::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    timereports.stream()
        .collect(groupingBy(BookingOnDay::dayOf, LinkedHashMap::new, Collectors.toList()))
        .forEach((day, savedOnDay) -> validateAbsenceDay(savedOnDay.getFirst().getEmployeecontract(), day.day(),
            savedOnDay, savedIds, previousStates));
  }

  private void validateAbsenceDay(Employeecontract employeecontract, LocalDate day, List<Timereport> savedOnDay,
      Set<Long> savedIds, List<StoredBooking> previousStates) {
    var others = timereportRepository.findAllByEmployeecontractIdAndReferencedayRefdate(employeecontract.getId(), day)
        .stream()
        .filter(timereport -> !savedIds.contains(timereport.getId()))
        .map(BookingOnDay::of)
        .toList();
    var after = savedOnDay.stream().map(BookingOnDay::of).toList();
    if (!BookingOnDay.anyAbsence(others, after)) {
      return;
    }
    var dayKey = new DayKey(employeecontract.getId(), day);
    var before = previousStates.stream().map(StoredBooking::onDay).filter(state -> state.day().equals(dayKey)).toList();

    Duration target = WorkingTimeTarget.between(employeecontract, day, day, publicholidayDAO::getPublicHolidaysBetween);
    Duration bookedByOthers = BookingOnDay.workingTimeOf(others);
    Duration excessAfter = bookedByOthers.plus(BookingOnDay.workingTimeOf(after)).minus(target);
    // before the change the day may not have had an absence at all, and then it had no limit to exceed
    Duration excessBefore = BookingOnDay.anyAbsence(others, before)
        ? bookedByOthers.plus(BookingOnDay.workingTimeOf(before)).minus(target)
        : Duration.ZERO;
    boolean exceedsTarget = excessAfter.isPositive();
    boolean excessGrows = excessAfter.compareTo(excessBefore) > 0;
    if (!exceedsTarget || !excessGrows) {
      return;
    }

    var date = DateUtils.format(day);
    var sign = employeecontract.getEmployee().getSign();
    if (!target.isPositive()) {
      throw new BusinessRuleException(TR_ABSENCE_DAY_WITHOUT_TARGET, date, sign);
    }
    var remaining = target.minus(bookedByOthers);
    throw new BusinessRuleException(TR_ABSENCE_DAY_EXCEEDS_TARGET, date, sign, DurationUtils.format(target),
        DurationUtils.format(remaining.isNegative() ? Duration.ZERO : remaining));
  }

  /** A day of a contract — the unit the limit of a day with an absence applies to (#397). */
  private record DayKey(long employeecontractId, LocalDate day) {
  }

  /**
   * A stored booking as it stood before a change, taken before the change is applied to it: the values the
   * limit of a day with an absence needs of its previous state (#397). Changing a booking replaces its employee
   * order and its day instead of altering them, so the references keep the old state.
   */
  /**
   * An existing booking as it stood before a change: what the limit of a day with an absence compares with
   * (#397), and the time surcharge flag and rate it keeps as long as kind and suborder stay (#1469).
   */
  private record StoredBooking(Long timereportId, Employeeorder employeeorder, LocalDate day, Duration duration,
                               SurchargeKind surchargeKind, Integer surchargePercent) {

    static StoredBooking of(Timereport timereport) {
      return new StoredBooking(timereport.getId(), timereport.getEmployeeorder(), timereport.getReferenceday().getRefdate(),
          timereport.getDuration(), timereport.getSurchargeKind(), timereport.getSurchargePercent());
    }

    BookingOnDay onDay() {
      return BookingOnDay.of(employeeorder, day, duration);
    }

    /** Whether the booking keeps its stored rate: same kind on the same suborder. */
    boolean keepsSurchargeRate(SurchargeKind newKind, Suborder newSuborder) {
      return surchargeKind != null && surchargePercent != null && surchargeKind == newKind
          && Objects.equals(employeeorder.getSuborder().getId(), newSuborder.getId());
    }
  }

  /** A booking as far as the limit of a day with an absence asks about it (#397). */
  private record BookingOnDay(DayKey day, Duration workingTime, boolean absence) {

    static BookingOnDay of(Timereport timereport) {
      return of(timereport.getEmployeeorder(), timereport.getReferenceday().getRefdate(), timereport.getDuration());
    }

    static BookingOnDay of(Employeeorder employeeorder, LocalDate day, Duration duration) {
      OrderType orderType = employeeorder.getSuborder().getEffectiveOrderType();
      // no type at all is a standard order, as in TimereportRepository.IS_WORKING_TIME
      boolean workingTime = orderType == null || orderType.isWorkingTime();
      return new BookingOnDay(new DayKey(employeeorder.getEmployeecontract().getId(), day),
          workingTime ? duration : Duration.ZERO, orderType == OrderType.KRANK_URLAUB_ABWESEND);
    }

    static DayKey dayOf(Timereport timereport) {
      return new DayKey(timereport.getEmployeecontract().getId(), timereport.getReferenceday().getRefdate());
    }

    static boolean anyAbsence(List<BookingOnDay> first, List<BookingOnDay> second) {
      return first.stream().anyMatch(BookingOnDay::absence) || second.stream().anyMatch(BookingOnDay::absence);
    }

    static Duration workingTimeOf(List<BookingOnDay> bookings) {
      return bookings.stream().map(BookingOnDay::workingTime).reduce(Duration.ZERO, Duration::plus);
    }
  }

  private void validateEmployeeorderBudget(List<Timereport> timereports) throws BusinessRuleException {
    // one timereport exists at least and all share the same suborder & employeeorder
    Employeeorder employeeorder = timereports.getFirst().getEmployeeorder();
    if(employeeorder.getDebithoursunit() == null) {
      return; // no budget defined!
    }
    final long debitMinutes = getDebitMinutes(timereports, employeeorder);
    switch(employeeorder.getDebithoursunit()) {
      case DEBITHOURS_UNIT_MONTH:
        Map<YearMonth, Long> minutesPerYearMonth = timereports.stream()
            .collect(groupingBy(t -> getYearMonth(t.getReferenceday().getRefdate()),
                summingLong(t -> t.getDuration().toMinutes())));
        minutesPerYearMonth.forEach((yearMonth, minutesSum) -> {
          long alreadyReportedMinutes = timereportDAO.getTotalDurationMinutesForEmployeeOrder(
              employeeorder.getId(),
              getFirstDay(yearMonth),
              getLastDay(yearMonth)
          );
          BusinessRuleCheckUtils.isTrue(alreadyReportedMinutes + minutesSum <= debitMinutes,
              TR_MONTH_BUDGET_EXCEEDED);
        });
        break;
      case DEBITHOURS_UNIT_YEAR:
        Map<Year, Long> minutesPerYear = timereports.stream()
            .collect(groupingBy(t -> getYear(t.getReferenceday().getRefdate()),
                summingLong(t -> t.getDuration().toMinutes())));
        minutesPerYear.forEach((year, minutesSum) -> {
          long alreadyReportedMinutes = timereportDAO.getTotalDurationMinutesForEmployeeOrder(
              employeeorder.getId(),
              getFirstDay(year),
              getLastDay(year)
          );
          BusinessRuleCheckUtils.isTrue(alreadyReportedMinutes + minutesSum <= debitMinutes,
              TR_YEAR_BUDGET_EXCEEDED);
        });
        break;
      case DEBITHOURS_UNIT_TOTALTIME:
        long minutesSum = timereports.stream()
            .mapToLong(t -> t.getDuration().toMinutes())
            .sum();
        long alreadyReportedMinutes = timereportDAO
            .getTotalDurationMinutesForEmployeeOrder(employeeorder.getId());
        BusinessRuleCheckUtils.isTrue(alreadyReportedMinutes + minutesSum <= debitMinutes,
            TR_TOTAL_BUDGET_EXCEEDED);
        break;
      default:
        throw new IllegalStateException(
            "employeeorder has unsupported debit hours unit: "
            + employeeorder.getDebithoursunit()
        );
    }
  }

  private static long getDebitMinutes(List<Timereport> timereports, Employeeorder employeeorder) {
    long debitMinutesTemp = employeeorder.getDebithours().toMinutes();
    // increase debit minutes if timereport exists (update case) by the time of that timereport
    // because this time is read from the database query, too. This is a trick to circumvent this special case.
    if(timereports.size() == 1 && !timereports.getFirst().isNew()) {
      Timereport timereport = timereports.getFirst();
      debitMinutesTemp += timereport.getDuration().toMinutes();
    }
    final long debitMinutes = debitMinutesTemp;
    return debitMinutes;
  }

  private void validateOrderBusinessRules(List<Timereport> timereports) throws BusinessRuleException {
    // one timereport exists at least and all share the same order and comment
    Timereport timereport = timereports.getFirst();
    Suborder suborder = timereport.getSuborder();
    if(TRUE.equals(suborder.getCommentnecessary())) {
      BusinessRuleCheckUtils.notEmpty(timereport.getTaskdescription(), TR_SUBORDER_COMMENT_MANDATORY);
    }
    // but every serial day has a date of its own, and each must lie within the order (#1429)
    for (var serialDay : timereports) {
      LocalDate refdate = serialDay.getReferenceday().getRefdate();
      if (!serialDay.getEmployeeorder().isValidAt(refdate)) {
        throw new BusinessRuleException(TR_EMPLOYEE_ORDER_INVALID_REF_DATE, DateUtils.format(refdate));
      }
    }
  }

  /**
   * As many ticket references as the suborder allows (#1326), on every save — also when the references
   * did not change and only the setting has become stricter since. A booking that would no longer fit
   * is saved again only with fewer references; anything else would leave the count to whoever saves
   * last. Every write path comes through here: form, inline edit, REST, CSV import, favourite,
   * previous booking, shifting and moving to another suborder.
   */
  private void validateTicketReferences(List<Timereport> timereports) throws BusinessRuleException {
    for (var timereport : timereports) {
      var count = timereport.getTicketReferences().size();
      var policy = timereport.getSuborder().getEffectiveTicketReferencePolicy();
      if (policy.permits(count)) {
        continue;
      }
      var sign = timereport.getSuborder().getCompleteOrderSign();
      if (!policy.allowsAny()) {
        throw new BusinessRuleException(TR_TICKET_REFERENCES_NOT_ALLOWED, sign);
      }
      throw new BusinessRuleException(TR_TICKET_REFERENCES_EXCEED_LIMIT, sign, policy.limit(),
          DateUtils.format(timereport.getReferenceday().getRefdate()), count);
    }
  }

  /**
   * A Saturday surcharge only on a Saturday, a Sunday surcharge only on a Sunday, a holiday surcharge
   * only on a public holiday (#1469), the rule of {@link #getSurchargeDayKinds} - checked for every serial
   * day, and again when a flagged booking is shifted to another day. A night can fall on any day.
   */
  private void validateSurchargeDays(List<Timereport> timereports, List<StoredBooking> previousStates)
      throws BusinessRuleException {
    for (var timereport : timereports) {
      var kind = timereport.getSurchargeKind();
      var day = timereport.getReferenceday().getRefdate();
      if (isBoundToDay(kind) && !keepsItsDayAndFlag(timereport, previousStates)
          && !getSurchargeDayKinds(day).contains(kind)) {
        throw new BusinessRuleException(dayRefusalOf(kind), DateUtils.format(day));
      }
    }
  }

  private static boolean isBoundToDay(SurchargeKind kind) {
    return kind != null && kind.isBoundToDay();
  }

  /**
   * Whether an edited booking keeps the day and the flag it was stored with: the day was checked when it was
   * flagged, and a holiday taken off the calendar since must not lock the booking against every change.
   */
  private static boolean keepsItsDayAndFlag(Timereport timereport, List<StoredBooking> previousStates) {
    return previousStates.stream().anyMatch(previous -> Objects.equals(previous.timereportId(), timereport.getId())
        && previous.day().equals(timereport.getReferenceday().getRefdate())
        && previous.surchargeKind() == timereport.getSurchargeKind());
  }

  private static ErrorCode dayRefusalOf(SurchargeKind kind) {
    return switch (kind) {
      case SATURDAY -> TR_SURCHARGE_SATURDAY_ON_OTHER_DAY;
      case SUNDAY -> TR_SURCHARGE_SUNDAY_ON_OTHER_DAY;
      case HOLIDAY -> TR_SURCHARGE_HOLIDAY_ON_NON_HOLIDAY;
      case NIGHT -> throw new IllegalArgumentException("a night is bound to no day");
    };
  }

  /** Every serial day, not only the first: each has a date of its own (#1429). */
  private void validateContractBusinessRules(List<Timereport> timereports) throws BusinessRuleException {
    for (var serialDay : timereports) {
      LocalDate refdate = serialDay.getReferenceday().getRefdate();
      if (!serialDay.getEmployeecontract().isValidAt(refdate)) {
        throw new BusinessRuleException(TR_EMPLOYEE_CONTRACT_INVALID_REF_DATE, DateUtils.format(refdate));
      }
    }
  }

  private void validateWorkingDayBusinessRules(List<Timereport> timereports) {
    timereports.forEach(timereport -> {
      var employeeContractId = timereport.getEmployeecontract().getId();
      var workingDay = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(
          timereport.getReferenceday().getRefdate(),
          employeeContractId
      );

      // check begin and break rules
      OrderType orderType = timereport.getSuborder().getEffectiveOrderType();
      if (needsWorkingHoursLawValidation(employeeContractId) && isRelevantForWorkingTimeValidation(orderType)) {
        DataValidationUtils.notNull(workingDay, TR_WORKING_DAY_START_NULL);
        DataValidationUtils.notNull(workingDay.getStartOfWorkingDay(), TR_WORKING_DAY_START_NULL);
      }

      // if any time is reported the type of the working day must not be NOT_WORKED for that day
      boolean notWorked = workingDay != null && workingDay.getType() == NOT_WORKED;
      BusinessRuleCheckUtils.isFalse(notWorked, TR_WORKING_DAY_NOT_WORKED);
    });
  }

  private void validateTimeReportingBusinessRules(List<Timereport> timereports) {
    timereports.forEach(timereport -> {
      Year reportedYear = getYear(timereport.getReferenceday().getRefdate());
      // check date range (must be in current, previous or next year)
      BusinessRuleCheckUtils.isTrue(Math.abs(DateUtils.getCurrentYear() - reportedYear.getValue()) <= 1,
          TR_YEAR_OUT_OF_RANGE);

      DataValidationUtils.isTrue(timereport.getDuration().isPositive(), TR_DURATION_INVALID);
    });
  }

  public Map<LocalDate, ServiceFeedbackMessage> validateBreakTimes(long employeeContractId, LocalDate begin, LocalDate end) {
    if(!needsWorkingHoursLawValidation(employeeContractId)) {
      return Map.of(); // restricted/external users are not in scope of regulations by law
    }

    var timeReports = timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeeContractId, begin, end);
    var timeReportsByDate = timeReports.stream().collect(groupingBy(TimereportDTO::getReferenceday));
    var workingDays = workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, begin, end).stream().collect(toMap(Workingday::getRefday, identity()));

    Set<LocalDate> dates = timeReportsByDate.keySet();
    var errors = dates
        .stream()
        .map(date -> Pair.of(date, validateBreakTime(date, timeReportsByDate, workingDays)))
        .filter(pair -> pair.getSecond().isPresent())
        .collect(toMap(pair -> pair.getFirst(), pair -> pair.getSecond().get()));
    return errors;
  }

  public Map<LocalDate, ServiceFeedbackMessage> validateBeginOfWorkingDays(long employeeContractId, LocalDate begin, LocalDate end) {
    if(!needsWorkingHoursLawValidation(employeeContractId)) {
      return Map.of(); // restricted/external users are not in scope of regulations by law
    }

    var timeReports = timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeeContractId, begin, end);
    var timeReportsByDate = timeReports.stream().collect(groupingBy(TimereportDTO::getReferenceday));
    var workingDays = workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, begin, end).stream().collect(toMap(Workingday::getRefday, identity()));

    Set<LocalDate> dates = timeReportsByDate.keySet();
    var errors = dates
        .stream()
        .map(date -> Pair.of(date, validateBeginOfWorkingDay(date, timeReportsByDate, workingDays)))
        .filter(pair -> pair.getSecond().isPresent())
        .collect(toMap(pair -> pair.getFirst(), pair -> pair.getSecond().get()));
    return errors;
  }

  static boolean isRelevantForWorkingTimeValidation(OrderType orderType) {
    return orderType != null && orderType == OrderType.STANDARD;
  }

  public void updateReleaseData(long timereportId, String status, String releasedBy, LocalDateTime releasedDate,
      String acceptedBy, LocalDateTime acceptedDate) {
    Timereport timereport = timereportRepository.findById(timereportId).orElse(null);
    DataValidationUtils.notNull(timereport, TR_TIME_REPORT_NOT_FOUND);
    timereport.setStatus(status);
    timereport.setReleasedby(releasedBy);
    timereport.setReleased(releasedDate);
    timereport.setAcceptedby(acceptedBy);
    timereport.setAccepted(acceptedDate);
    timereportRepository.save(timereport);
    eventPublisher.publishEvent(new TimereportsCreatedOrUpdatedEvent(List.of(timereportId)));
  }

  @EventListener
  void onEmployeeorderUpdate(EmployeeorderUpdateEvent event) {
    var from = event.getDomainObject().getFromDate();
    var until = event.getDomainObject().getEffectiveUntilDate();
    var timereports = timereportDAO.getTimereportsByEmployeeorderIdInvalidForDates(event.getDomainObject().getId(), from, until);
    if(!timereports.isEmpty()) {
      var errors = timereports.stream()
          .map(tr -> error(
              TR_TIMEREPORTS_EXIST_CANNOT_DELETE_OR_UPDATE_EMPLOYEE_ORDER,
              tr.getCompleteOrderSign(),
              tr.getEmployeeSign(),
              tr.getReferenceday()
          ))
          .toList();
      event.veto(errors);
    }
  }

  @EventListener
  void onEmployeeorderDelete(EmployeeorderDeleteEvent event) {
    var employeeorderId = event.getId();
    List<TimereportDTO> timereports = timereportDAO.getTimereportsByEmployeeOrderId(employeeorderId);
    if(!timereports.isEmpty()) {
      var errors = timereports.stream()
          .map(tr -> error(
              TR_TIMEREPORTS_EXIST_CANNOT_DELETE_OR_UPDATE_EMPLOYEE_ORDER,
              tr.getCompleteOrderSign(),
              tr.getEmployeeSign(),
              tr.getReferenceday()
          ))
          .toList();
      event.veto(errors);
    }
    var deletedCount = timereportRepository.hardDeleteSoftDeletedByEmployeeorderId(employeeorderId); // ensure no soft deleted timereport exists
    log.info("{} soft deleted timereports were deleted to allow deletion of employee order {}.", deletedCount, employeeorderId);
  }

  @EventListener
  void onEmployeeorderConflictResolution(EmployeeorderConflictResolutionEvent event) {
    var updatingEmployeeorder = event.getUpdatingEmployeeorder();
    var conflictingEmployeeorder = event.getConflictingEmployeeorder();

    var timereports = timereportRepository.findAllByEmployeeorderIdAndReferencedayBetween(
        conflictingEmployeeorder.getId(),
        updatingEmployeeorder.getValidity().isInfiniteFrom() ? FINIT_FROM_BOUNDARY : updatingEmployeeorder.getFromDate(),
        updatingEmployeeorder.getValidity().isInfiniteUntil() ? FINIT_UNTIL_BOUNDARY : updatingEmployeeorder.getUntilDate()
    );

    // move reports - the status follows the release and acceptance dates of the new contract (#1128).
    // The old contract's dates are cut to its new end and do not move along, so a booking accepted
    // there is open again unless the new contract has accepted that day itself.
    Map<Long, Long> previousEmployeecontractIds = new HashMap<>();
    timereports.forEach(tr -> {
      previousEmployeecontractIds.put(tr.getId(), tr.getEmployeecontract().getId());
      tr.setEmployeeorder(updatingEmployeeorder);
      setStatus(tr);
      timereportRepository.save(tr);
      event.addLog("Buchung %s, %s, %s verschoben".formatted(tr.getReferenceday().getRefdate(), tr.getSuborder().getCompleteOrderSign(),
          DurationUtils.format(tr.getDuration())));
    });

    var timereportIds = timereports.stream().map(Timereport::getId).toList();
    eventPublisher.publishEvent(new TimereportsCreatedOrUpdatedEvent(timereportIds, Map.of(), previousEmployeecontractIds));
  }

  /**
   * Answers the jira module's question for the booked minutes per day and ticket (#1007), a booking
   * with several references split among them (#1326), and per person by sign how they are made up
   * (#1408). No per-person filter applies here and none is missing: the caller is the replication,
   * not a user, and what leaves is per day and ticket the sign of each person with a share of the
   * time — no name, no task description, nothing else of the booking. Which suborders may be asked
   * about is decided by the replication config, which only a manager can write.
   */
  @EventListener
  void handleCommandEvent(GetTicketWorklogSumsCommandEvent event) {
    var suborderIds = event.getSuborderIds();
    if (suborderIds == null || suborderIds.isEmpty()) {
      event.setResult(List.of());
      return;
    }
    event.setResult(TicketDaySums.of(timereportRepository.getBookedTicketReferences(
        suborderIds, event.getFrom(), event.getUntil(), event.isInvoiceableOnly())));
  }

  @EventListener
  void handleCommandEvent(GetTimereportMinutesCommandEvent event) {
    var ids = event.getOrderIds();
    switch(event.getOrderType()) {
      case CUSTOMER -> event.setResult(toDurationMap(timereportRepository.getReportedMinutesForCustomerordersAsMap(ids)));
      case SUB -> event.setResult(toDurationMap(timereportRepository.getReportedMinutesForSubordersAsMap(ids)));
      case EMPLOYEE -> event.setResult(toDurationMap(timereportRepository.getReportedMinutesForEmployeeordersAsMap(ids)));
    }
  }

  private Map<Long, Duration> toDurationMap(List<Long[]> minutesGrouped) {
    return minutesGrouped.stream()
        .collect(Collectors.toMap(
            cols -> cols[0],
            cols -> Duration.ofMinutes(cols[1])
        ));
  }

  // utility methods

  static Optional<ServiceFeedbackMessage> validateBeginOfWorkingDay(LocalDate date,
      Map<LocalDate, List<TimereportDTO>> timereports,
      Map<LocalDate, Workingday> workingDays) {
    if(!timereports.containsKey(date)) return empty();
    Duration workDurationSum = timereports.get(date).stream()
        .filter(timeReport -> isRelevantForWorkingTimeValidation(timeReport.getOrderType()))
        .map(TimereportDTO::getDuration)
        .reduce(Duration.ZERO, Duration::plus);
    if(!workDurationSum.isPositive()) {
      return empty(); // not worked = no validation
    }
    Workingday workingDay = workingDays.get(date);
    if (workingDay == null || workingDay.getStartOfWorkingDay() == null) {
      return of(error(WD_BEGIN_TIME_MISSING, date));
    }
    return empty();
  }

  static Optional<ServiceFeedbackMessage> validateBreakTime(LocalDate date,
      Map<LocalDate, List<TimereportDTO>> timeReports,
      Map<LocalDate, Workingday> workingDays) {
    if(!timeReports.containsKey(date)) return empty();
    Workingday workingDay = workingDays.get(date);
    Duration workDurationSum = timeReports.get(date).stream()
        .filter(timeReport -> isRelevantForWorkingTimeValidation(timeReport.getOrderType()))
        .map(TimereportDTO::getDuration)
        .reduce(Duration.ZERO, Duration::plus);
    if(!workDurationSum.isPositive()) return empty(); // not worked = no validation
    boolean notEnoughBreaksAfter9Hours = workingDay == null || workingDay.getBreakLengthInMinutes() < BREAK_MINUTES_AFTER_NINE_HOURS;
    boolean notEnoughBreaksAfter6Hours = workingDay == null || workingDay.getBreakLengthInMinutes() < BREAK_MINUTES_AFTER_SIX_HOURS;
    if (workDurationSum.toMinutes() > NINE_HOURS_IN_MINUTES && notEnoughBreaksAfter9Hours) {
      return of(error(WD_BREAK_TOO_SHORT_9, date));
    } else if (workDurationSum.toMinutes() > SIX_HOURS_IN_MINUTES && notEnoughBreaksAfter6Hours) {
      return of(error(WD_BREAK_TOO_SHORT_6, date));
    }
    return empty();
  }

  static boolean noTimeReportsFound(Map<LocalDate, List<TimereportDTO>> timeReports, LocalDate date) {
    return !timeReports.containsKey(date);
  }

  public List<TimereportDTO> getTimereportsForEmployeecontractAndDate(long employeecontractId, LocalDate date) {
    return timereportDAO.getTimereportsByDateAndEmployeeContractId(employeecontractId, date);
  }

  public List<TimereportDTO> getTimereportsByIds(Collection<Long> ids) {
    return timereportDAO.getTimereportsByIds(ids);
  }

  public List<TimereportDTO> getTimereportsByDates(LocalDate beginDate, LocalDate endDate) {
    return timereportDAO.getTimereportsByDates(beginDate, endDate);
  }

  public List<TimereportDTO> getTimereportsByDatesAndEmployeeContractId(long employeecontractId, LocalDate beginDate,
      LocalDate endDate) {
    return timereportDAO.getTimereportsByDatesAndEmployeeContractId(employeecontractId, beginDate, endDate);
  }

  public List<TimereportDTO> getTimereportsByDatesAndCustomerOrderId(LocalDate beginDate, LocalDate endDate,
      long orderId) {
    return timereportDAO.getTimereportsByDatesAndCustomerOrderId(beginDate, endDate, orderId);
  }

  /** The customer orders anything was booked on in the period (#779). */
  public List<String> getCustomerorderSignsWithReportsBetween(LocalDate beginDate, LocalDate endDate) {
    return timereportRepository.findDistinctCustomerorderSignsBetween(beginDate, endDate);
  }

  public List<TimereportDTO> getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(long employeecontractId, LocalDate beginDate,
      LocalDate endDate, long orderId) {
    return timereportDAO.getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(employeecontractId, beginDate, endDate, orderId);
  }

  public List<TimereportDTO> getTimereportsByDatesAndSuborderId(LocalDate beginDate, LocalDate endDate,
      long suborderId) {
    return timereportDAO.getTimereportsByDatesAndSuborderId(beginDate, endDate, suborderId);
  }

  public List<TimereportDTO> getTimereportsByDatesAndEmployeeContractIdAndSuborderId(long employeecontractId, LocalDate beginDate,
      LocalDate endDate, long suborderId) {
    return timereportDAO.getTimereportsByDatesAndEmployeeContractIdAndSuborderId(employeecontractId, beginDate, endDate, suborderId);
  }

  /**
   * The bookings of the contract in the range as „Meine Konten“ reads them (#1514), in one statement. They are not
   * checked one by one against {@link TimereportAuthorization#isAuthorized}: the contract is checked as a whole
   * ({@link TimereportAuthorization#mayReadEveryBookingOf}), and a contract that fails that check yields an
   * {@link AuthorizationException} rather than the few bookings that might be readable on other grounds.
   */
  public List<AccountBooking> getAccountBookingsOfContract(long employeecontractId, LocalDate begin, LocalDate end) {
    checkMayReadEveryBookingOf(employeecontractId);
    return timereportRepository.findAccountBookingsOfContractBetween(employeecontractId, begin, end);
  }

  /**
   * The bookings on the employee orders of the contract in the range, as {@link #getAccountBookingsOfContract} reads
   * them; an employee order of another contract contributes none.
   */
  public List<AccountBooking> getAccountBookingsOfEmployeeorders(long employeecontractId,
      Collection<Long> employeeorderIds, LocalDate begin, LocalDate end) {
    checkMayReadEveryBookingOf(employeecontractId);
    if (employeeorderIds.isEmpty()) {
      return List.of();
    }
    return timereportRepository.findAccountBookingsOfEmployeeordersBetween(employeecontractId, employeeorderIds, begin,
        end);
  }

  private void checkMayReadEveryBookingOf(long employeecontractId) {
    var contract = employeecontractDAO.getEmployeecontractById(employeecontractId);
    if (contract == null || !timereportAuthorization.mayReadEveryBookingOf(contract)) {
      throw new AuthorizationException(AA_NOT_ATHORIZED);
    }
  }

  public List<TimereportDTO> getTimereportsByDate(LocalDate date) {
    return timereportDAO.getTimereportsByDate(date);
  }

  public List<TimereportDTO> getTimereportsByDateAndEmployeeContractId(long employeeContractId, LocalDate date) {
    return timereportDAO.getTimereportsByDateAndEmployeeContractId(employeeContractId, date);
  }

  public long getTotalDurationMinutesForSuborderAndEmployeeContract(long soId, long ecId) {
    return timereportRepository.getReportedMinutesForSuborderAndEmployeeContract(soId, ecId).orElse(0L);
  }

  /** Like {@link #getTotalDurationMinutesForSuborderAndEmployeeContract}, only the bookings after {@code day}. */
  public long getTotalDurationMinutesForSuborderAndEmployeeContractAfter(long soId, long ecId, LocalDate day) {
    return timereportRepository.getReportedMinutesForSuborderAndEmployeeContractAfter(soId, ecId, day).orElse(0L);
  }

  /**
   * Gets the sum of all duration minutes WITH considering the hours.
   */
  public long getTotalDurationMinutesForSuborders(List<Long> ids) {
    if (ids == null || ids.isEmpty()) return 0;
    return timereportRepository.getReportedMinutesForSuborders(ids).orElse(0L);
  }

  /**
   * Gets the sum of all duration minutes within a range of time WITH considering the hours.
   */
  public long getTotalDurationMinutesForSuborder(long soId, LocalDate fromDate, LocalDate untilDate) {
    return timereportRepository.getReportedMinutesForSuborderAndBetween(soId, fromDate, untilDate).orElse(0L);
  }

  /**
   * Gets the sum of all duration minutes WITH consideration of the hours.
   */
  public long getTotalDurationMinutesForCustomerOrder(long coId) {
    return timereportRepository.getReportedMinutesForCustomerorder(coId).orElse(0L);
  }

  /**
   * Gets the sum of all duration minutes WITH considering the hours.
   */
  public long getTotalDurationMinutesForEmployeeOrder(long eoId) {
    return timereportRepository.getReportedMinutesForEmployeeorder(eoId).orElse(0L);
  }

  /**
   * Gets the sum of all duration minutes WITH considering the hours.
   */
  public long getTotalDurationMinutesForEmployeeOrder(long eoId, LocalDate fromDate, LocalDate untilDate) {
    return timereportRepository.getReportedMinutesForEmployeeorder(eoId, fromDate, untilDate).orElse(0L);
  }

  /**
   * Gets a list of all {@link TimereportDTO}s that fulfill following criteria:
   * 1) associated to the given employee contract
   * 2) refdate out of range of the employee contract
   *
   * @return Returns a {@link List} with all {@link TimereportDTO}s, that fulfill the criteria.
   */
  public List<TimereportDTO> getTimereportsOutOfRangeForEmployeeContract(long employeecontractId) {
    return timereportDAO.getTimereportsOutOfRangeForEmployeeContract(employeecontractId);
  }

  /**
   * Gets a list of all {@link TimereportDTO}s that fulfill following criteria:
   * 1) associated to the given employee contract
   * 2) refdate out of range of the associated employee order
   *
   * @return Returns a {@link List} with all {@link TimereportDTO}s, that fulfill the criteria.
   */
  public List<TimereportDTO> getTimereportsOutOfRangeForEmployeeOrder(long employeecontractId) {
    return timereportDAO.getTimereportsOutOfRangeForEmployeeOrder(employeecontractId);
  }

  @Transactional(readOnly = true)
  public List<RecentBooking> getRecentBookings(long employeeContractId, long suborderId) {
    return timereportDAO.getRecentBookingsByEmployeeContractIdAndSuborderId(employeeContractId, suborderId);
  }

  /** what the days before {@code date} offer a booking of that day for reuse (#1017) */
  @Transactional(readOnly = true)
  public List<PreviousBooking> getPreviousBookings(long employeeContractId, LocalDate date) {
    return timereportDAO.getPreviousBookingsByEmployeeContractId(employeeContractId, date);
  }

  @Transactional(readOnly = true)
  public List<SharedBookingRecipient> getEligibleShareRecipients(long suborderId, LocalDate referenceDate, long currentEmployeeContractId) {
    var employeeorders = employeeorderDAO.getEmployeeOrdersBySuborderId(suborderId);

    return employeeorders.stream()
        .filter(eo -> eo.isValidAt(referenceDate))
        .filter(eo -> eo.getEmployeecontract().getId() != currentEmployeeContractId)
        .filter(eo -> eo.getEmployeecontract().getHide() != TRUE)
        .filter(eo -> eo.getEmployeecontract().getEmployee().getHide() != TRUE)
        .map(eo -> new SharedBookingRecipient(
            eo.getEmployeecontract().getEmployee().getSalatUser().getId(),
            eo.getEmployeecontract().getEmployee().getSign(),
            eo.getEmployeecontract().getEmployee().getName()
        ))
        .distinct()
        .sorted((a, b) -> a.sign().compareTo(b.sign()))
        .collect(Collectors.toList());
  }

  public static record SharedBookingRecipient(Long employeeId, String sign, String name) {}

}