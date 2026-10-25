package de.hbt.salat.dailyreport.graphql;

import static java.time.temporal.ChronoUnit.DAYS;
import static de.hbt.salat.common.exception.ErrorCode.GQ_OFFSET_NEGATIVE;
import static de.hbt.salat.common.exception.ErrorCode.GQ_PAGE_SIZE_OUT_OF_RANGE;
import static de.hbt.salat.common.exception.ErrorCode.GQ_PERIOD_TOO_LONG;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.TR_TIME_REPORT_NOT_FOUND;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import de.hbt.salat.dailyreport.service.OwnTimereportService;
import de.hbt.salat.dailyreport.service.SurchargeChoice;
import de.hbt.salat.dailyreport.service.TimereportListService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.order.domain.SuborderLocation;
import de.hbt.salat.order.service.CustomerorderService;
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
  private final OwnTimereportService ownTimereportService;
  private final EmployeeorderService employeeorderService;
  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;
  private final AuthorizedEmployee authorizedEmployee;
  private final OwnContract ownContract;
  private final IdempotencyService idempotencyService;

  @QueryMapping
  @Authorized
  List<TimereportView> timereports(@Argument LocalDate from, @Argument LocalDate until,
      @Argument String customerorderSign, @Argument Integer offset, @Argument Integer limit) {
    checkPeriod(from, until);
    // without a limit every booking, as before there was one (#1539)
    int maxResults = limit == null ? Integer.MAX_VALUE : checkLimit(limit);
    return ownTimereportService.getOwnTimereports(from, until, customerorderSign, checkOffset(offset), maxResults)
        .stream()
        .map(TimereportView::of)
        .toList();
  }

  /** A single booking, {@code null} where there is none or the user may not read it (concept 6.3). */
  @QueryMapping
  @Authorized
  TimereportView timereport(@Argument long id) {
    TimereportDTO timereport = timereportService.getTimereportById(id);
    return timereport == null ? null : TimereportView.of(timereport);
  }

  /**
   * The booking list of the pages (concept 3.4, 5.1): the same requirement as {@code TimereportListController}, the
   * same service and with it the same {@code TimereportVisibility}. This method only translates the filter.
   */
  @QueryMapping
  @Authorized(requireUnrestricted = true)
  TimereportPage timereportList(@Argument TimereportListFilterInput filter) {
    checkPeriod(filter.from(), filter.until());
    int limit = checkLimit(Objects.requireNonNullElse(filter.limit(), DEFAULT_LIMIT));
    int offset = checkOffset(filter.offset());
    var customerorderIds = customerorderIdsOf(filter);
    if (hasSigns(filter) && customerorderIds.isEmpty()) {
      // only unknown signs: an empty list would mean no restriction, and a typo must not return every booking
      return TimereportPage.EMPTY;
    }
    var result = timereportListService.search(TimereportListFilter.of(filter.from(), filter.until(),
        filter.employeeIds(), filter.customerIds(), customerorderIds, filter.suborderIds(), filter.billable(),
        limit, offset));
    var items = result.timereports().stream().map(TimereportView::of).toList();
    return new TimereportPage(items, result.totalCount(), result.totalDuration(), result.billableDuration(),
        offset + items.size() < result.totalCount());
  }

  /**
   * The orders of the filter, by id and by sign together — both name the same dimension, so either list counts. A sign
   * no order carries is left out.
   */
  private List<Long> customerorderIdsOf(TimereportListFilterInput filter) {
    if (!hasSigns(filter)) {
      return filter.customerorderIds();
    }
    var bySign = customerorderService.getCustomerorderIdsBySigns(filter.customerorderSigns()).values();
    return Stream.concat(Objects.requireNonNullElse(filter.customerorderIds(), List.<Long>of()).stream(),
        bySign.stream()).distinct().toList();
  }

  private static boolean hasSigns(TimereportListFilterInput filter) {
    return filter.customerorderSigns() != null && !filter.customerorderSigns().isEmpty();
  }

  /** One call for all bookings of a response, instead of one per booking (concept 6.2). */
  @BatchMapping(typeName = "Timereport")
  @Authorized
  List<SuborderRef> suborder(List<TimereportView> timereports) {
    Set<Long> ids = timereports.stream().map(TimereportView::suborderId).collect(Collectors.toSet());
    Map<Long, SuborderLocation> locations = suborderService.getSuborderLocationsByIds(ids);
    // in the order of the bookings, which is how the batch is answered
    return timereports.stream().map(timereport -> SuborderRef.of(locations.get(timereport.suborderId()))).toList();
  }

  @MutationMapping
  @Authorized
  TimereportPayload createTimereport(@Argument String idempotencyKey, @Argument CreateTimereportInput input) {
    var outcome = idempotencyService.createOnce("createTimereport", idempotencyKey, input.canonical(),
        () -> create(input));
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
    // on another day the booking takes the contract of its person there (#1128) and the employee order valid then,
    // as the page determines it on every save: an employee order may be split into periods
    long contractId = current.getEmployeecontractId();
    long employeeorderId = current.getEmployeeorderId();
    if (!date.equals(current.getReferenceday())) {
      contractId = timereportService.getEmployeecontractIdForUpdate(id, date);
      employeeorderId = employeeorderService.getEmployeeorderId(contractId, current.getSuborderId(), date)
          .orElseThrow(() -> new InvalidDataException(TR_EMPLOYEE_ORDER_NOT_FOUND));
    }
    timereportService.updateTimereport(id, version, contractId, employeeorderId, date,
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

  private static int checkLimit(int limit) {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new InvalidDataException(GQ_PAGE_SIZE_OUT_OF_RANGE, MAX_LIMIT);
    }
    return limit;
  }

  /** {@code null} is the first page; the schema's default does not reach a variable sent as {@code null}. */
  private static int checkOffset(Integer offset) {
    if (offset != null && offset < 0) {
      throw new InvalidDataException(GQ_OFFSET_NEGATIVE);
    }
    return Objects.requireNonNullElse(offset, 0);
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

  /** The booking as the schema shows it. */
  public record TimereportView(long id, int version, LocalDate date, Duration duration, String taskDescription,
      List<String> ticketReferences, TimereportStatus status, boolean training, long employeeorderId, long suborderId,
      PersonRef employee) {

    static TimereportView of(TimereportDTO dto) {
      return new TimereportView(dto.getId(), Objects.requireNonNullElse(dto.getVersion(), 0), dto.getReferenceday(),
          dto.getDuration(), dto.getTaskdescription(), dto.getTicketReferences(),
          TimereportStatus.valueOf(dto.getStatus().toUpperCase(Locale.ROOT)), dto.isTraining(),
          dto.getEmployeeorderId(), dto.getSuborderId(),
          new PersonRef(dto.getEmployeeId(), dto.getEmployeeSign(), dto.getEmployeeName()));
    }
  }

  public record PersonRef(long id, String sign, String name) {
  }

  /** The status as the schema names it; stored as {@code open}, {@code committed}, {@code closed}. */
  public enum TimereportStatus {
    OPEN, COMMITTED, CLOSED
  }

  public record SuborderRef(long id, String completeOrderSign) {

    static SuborderRef of(SuborderLocation location) {
      return new SuborderRef(location.id(), location.completeOrderSign());
    }
  }

  public record TimereportListFilterInput(LocalDate from, LocalDate until, List<Long> employeeIds, List<Long> customerIds,
      List<Long> customerorderIds, List<String> customerorderSigns, List<Long> suborderIds, Billable billable,
      Integer limit, Integer offset) {
  }

  /** @param truncated whether there are hits behind this page */
  public record TimereportPage(List<TimereportView> items, long totalCount, Duration totalDuration,
      Duration billableDuration, boolean truncated) {

    static final TimereportPage EMPTY = new TimereportPage(List.of(), 0, Duration.ZERO, Duration.ZERO, false);
  }

  public record CreateTimereportInput(long suborderId, LocalDate date, Duration duration, String taskDescription,
      List<String> ticketReferences) {

    /**
     * What makes two calls the same booking, for the idempotency key: no references and an empty list are the same.
     * A new optional field enters only where it changes the booking, with its default left out, so that a repetition
     * across a deployment keeps its hash.
     */
    List<Object> canonical() {
      return List.of(suborderId, date, duration, taskDescription,
          ticketReferences == null ? List.of() : ticketReferences);
    }
  }

  public record UpdateTimereportInput(LocalDate date, Duration duration, String taskDescription,
      List<String> ticketReferences) {
  }

  public record TimereportPayload(TimereportView timereport, boolean replayed) {
  }

  public record DeletePayload(long id) {
  }
}
