package de.hbt.salat.dailyreport.graphql;

import static java.time.temporal.ChronoUnit.DAYS;
import static de.hbt.salat.common.exception.ErrorCode.GQ_PAGE_SIZE_OUT_OF_RANGE;
import static de.hbt.salat.common.exception.ErrorCode.GQ_PERIOD_TOO_LONG;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.TR_TIME_REPORT_NOT_FOUND;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.service.IdempotencyService;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.domain.TimereportListFilter;
import de.hbt.salat.dailyreport.domain.TimereportListFilter.Billable;
import de.hbt.salat.dailyreport.service.SurchargeChoice;
import de.hbt.salat.dailyreport.service.TimereportListService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.SuborderLocation;
import de.hbt.salat.order.service.EmployeeorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Bookings over GraphQL (concept stage 1 and 2): reading the own ones and the booking list, creating, changing and
 * deleting. A thin layer over {@link TimereportService} and {@link TimereportListService}, like a controller
 * (concept 3.1) — who may read and write what is the services' answer, not this class's.
 */
@Slf4j
@Controller
@Authorized
@RequiredArgsConstructor
class TimereportResolver {

  static final int MAX_DAYS = 366;
  /** The page size of the concept (6.2). */
  static final int MAX_LIMIT = 200;
  static final int DEFAULT_LIMIT = 100;

  private final TimereportService timereportService;
  private final TimereportListService timereportListService;
  private final EmployeecontractService employeecontractService;
  private final EmployeeorderService employeeorderService;
  private final SuborderService suborderService;
  private final AuthorizedEmployee authorizedEmployee;
  private final OwnContract ownContract;
  private final IdempotencyService idempotencyService;

  @QueryMapping
  @Authorized
  List<TimereportView> timereports(@Argument LocalDate from, @Argument LocalDate until) {
    checkPeriod(from, until);
    // the same two steps as DailyReportRestEndpoint: the contracts covering the period, then their bookings
    return employeecontractService.getEmployeecontractPeriodsBetween(authorizedEmployee.getEmployeeId(), from, until)
        .stream()
        .flatMap(period -> timereportService
            .getTimereportsByDatesAndEmployeeContractId(period.employeecontractId(), period.from(), period.until())
            .stream())
        .map(TimereportView::of)
        .toList();
  }

  /**
   * The booking list of the pages (concept 3.4, 5.1): the same requirement as {@code TimereportListController}, the
   * same service and with it the same {@code TimereportVisibility}. This method only translates the filter.
   */
  @QueryMapping
  @Authorized(requireUnrestricted = true)
  TimereportPage timereportList(@Argument TimereportListFilterInput filter) {
    checkPeriod(filter.from(), filter.until());
    int limit = Objects.requireNonNullElse(filter.limit(), DEFAULT_LIMIT);
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new InvalidDataException(GQ_PAGE_SIZE_OUT_OF_RANGE, MAX_LIMIT);
    }
    var result = timereportListService.search(TimereportListFilter.of(filter.from(), filter.until(),
        filter.employeeIds(), filter.customerIds(), filter.customerorderIds(), filter.suborderIds(), filter.billable(),
        limit));
    return new TimereportPage(result.timereports().stream().map(TimereportView::of).toList(), result.totalCount(),
        result.totalDuration(), result.billableDuration(), result.truncated());
  }

  /** One call for all bookings of a response, instead of one per booking (concept 6.2). */
  @BatchMapping(typeName = "Timereport")
  @Authorized
  Map<TimereportView, SuborderRef> suborder(List<TimereportView> timereports) {
    Set<Long> ids = timereports.stream().map(TimereportView::suborderId).collect(Collectors.toSet());
    Map<Long, SuborderLocation> locations = suborderService.getSuborderLocationsByIds(ids);
    return timereports.stream().collect(Collectors.toMap(Function.identity(),
        timereport -> SuborderRef.of(locations.get(timereport.suborderId())), (a, b) -> a));
  }

  @MutationMapping
  @Authorized
  TimereportPayload createTimereport(@Argument String idempotencyKey, @Argument CreateTimereportInput input) {
    var outcome = idempotencyService.createOnce("createTimereport", idempotencyKey, input, () -> create(input));
    log.info("GraphQL createTimereport {} by {}{}", outcome.id(), authorizedEmployee.getSign(),
        outcome.replayed() ? " (repeated)" : "");
    return new TimereportPayload(read(outcome.id()), outcome.replayed());
  }

  /**
   * Changes a booking the caller read in {@code version}: a booking changed since is refused with {@code XX-0003}
   * instead of overwritten (concept 3.5). Fields not given keep what the booking has.
   */
  @MutationMapping
  @Authorized
  TimereportPayload updateTimereport(@Argument long id, @Argument int version, @Argument UpdateTimereportInput input) {
    TimereportDTO current = readDto(id);
    LocalDate date = Objects.requireNonNullElse(input.date(), current.getReferenceday());
    // across a contract boundary the booking takes the contract of its person on the new day (#1128)
    long contractId = date.equals(current.getReferenceday())
        ? current.getEmployeecontractId()
        : timereportService.getEmployeecontractIdForUpdate(id, date);
    timereportService.updateTimereport(id, version, contractId, current.getEmployeeorderId(), date,
        Objects.requireNonNullElse(input.taskDescription(), current.getTaskdescription()),
        Objects.requireNonNullElse(input.ticketReferences(), current.getTicketReferences()),
        current.isTraining(), current.getSurchargeKind(),
        Objects.requireNonNullElse(input.duration(), current.getDuration()));
    log.info("GraphQL updateTimereport {} by {}", id, authorizedEmployee.getSign());
    return new TimereportPayload(read(id), false);
  }

  /** Deletes softly, like the page; the status rule of the period applies. */
  @MutationMapping
  @Authorized
  DeletePayload deleteTimereport(@Argument long id) {
    readDto(id);
    timereportService.deleteTimereportById(id);
    log.info("GraphQL deleteTimereport {} by {}", id, authorizedEmployee.getSign());
    return new DeletePayload(id);
  }

  private long create(CreateTimereportInput input) {
    long contractId = ownContract.on(input.date());
    long employeeorderId = employeeorderService.getEmployeeorderId(contractId, input.suborderId(), input.date())
        .orElseThrow(() -> new InvalidDataException(TR_EMPLOYEE_ORDER_NOT_FOUND));
    return timereportService.createTimereports(contractId, employeeorderId, input.date(), input.taskDescription(),
        input.ticketReferences(), false, SurchargeChoice.NONE, input.duration(), 1).getFirst();
  }

  private static void checkPeriod(LocalDate from, LocalDate until) {
    if (until.isBefore(from) || DAYS.between(from, until) >= MAX_DAYS) {
      throw new InvalidDataException(GQ_PERIOD_TOO_LONG, MAX_DAYS);
    }
  }

  private TimereportView read(long id) {
    return TimereportView.of(readDto(id));
  }

  /** Not found and not readable look the same (concept 6.3). */
  private TimereportDTO readDto(long id) {
    TimereportDTO timereport = timereportService.getTimereportById(id);
    if (timereport == null) {
      throw new InvalidDataException(TR_TIME_REPORT_NOT_FOUND);
    }
    return timereport;
  }

  /** The booking as the schema shows it; equal by id, so that the batch loader can key by it. */
  public record TimereportView(long id, int version, LocalDate date, Duration duration, String taskDescription,
      List<String> ticketReferences, String status, boolean training, long employeeorderId, long suborderId,
      PersonRef employee) {

    static TimereportView of(TimereportDTO dto) {
      return new TimereportView(dto.getId(), Objects.requireNonNullElse(dto.getVersion(), 0), dto.getReferenceday(),
          dto.getDuration(), dto.getTaskdescription(), dto.getTicketReferences(), dto.getStatus(), dto.isTraining(),
          dto.getEmployeeorderId(), dto.getSuborderId(),
          new PersonRef(dto.getEmployeeId(), dto.getEmployeeSign(), dto.getEmployeeName()));
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof TimereportView view && view.id == id;
    }

    @Override
    public int hashCode() {
      return Long.hashCode(id);
    }
  }

  public record PersonRef(long id, String sign, String name) {
  }

  public record SuborderRef(long id, String completeOrderSign) {

    static SuborderRef of(SuborderLocation location) {
      return new SuborderRef(location.id(), location.completeOrderSign());
    }
  }

  public record TimereportListFilterInput(LocalDate from, LocalDate until, List<Long> employeeIds, List<Long> customerIds,
      List<Long> customerorderIds, List<Long> suborderIds, Billable billable, Integer limit) {
  }

  public record TimereportPage(List<TimereportView> items, long totalCount, Duration totalDuration,
      Duration billableDuration, boolean truncated) {
  }

  public record CreateTimereportInput(long suborderId, LocalDate date, Duration duration, String taskDescription,
      List<String> ticketReferences) {
  }

  public record UpdateTimereportInput(LocalDate date, Duration duration, String taskDescription,
      List<String> ticketReferences) {
  }

  public record TimereportPayload(TimereportView timereport, boolean replayed) {
  }

  public record DeletePayload(long id) {
  }
}
