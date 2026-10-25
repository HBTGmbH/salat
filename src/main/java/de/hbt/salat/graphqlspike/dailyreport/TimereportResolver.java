package de.hbt.salat.graphqlspike.dailyreport;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Controller;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.dailyreport.service.SurchargeChoice;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.EmployeecontractPeriod;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.graphqlspike.config.GraphqlSpikeConfiguration;
import de.hbt.salat.graphqlspike.config.SpikeException;
import de.hbt.salat.graphqlspike.config.SpikeIdempotency;
import de.hbt.salat.order.domain.SuborderLocation;
import de.hbt.salat.order.service.SuborderService;

/**
 * The own bookings (concept stage 1 and 2): a thin layer over {@link TimereportService}, like a controller
 * (concept 3.1). Who may read and write what is the service's answer, not this class's.
 *
 * <p>Where the services fall short of the concept, the spike works around it here instead of changing them —
 * each workaround is a finding: {@code createTimereports} returns no id, so the new booking is found by
 * comparing the day before and after; the input names the employee order, because finding it from suborder
 * and day is a method returning the entity; and {@link TimereportDTO} carries no version to check.
 */
@Controller
@Profile(GraphqlSpikeConfiguration.PROFILE)
@Authorized
@RequiredArgsConstructor
class TimereportResolver {

  static final int MAX_DAYS = 366;

  private final TimereportService timereportService;
  private final EmployeecontractService employeecontractService;
  private final SuborderService suborderService;
  private final AuthorizedEmployee authorizedEmployee;
  private final SpikeIdempotency idempotency;

  @QueryMapping
  @Authorized
  List<TimereportView> timereports(@Argument LocalDate from, @Argument LocalDate until) {
    if (until.isBefore(from) || DAYS.between(from, until) >= MAX_DAYS) {
      throw new SpikeException(SpikeException.INVALID_INPUT, ErrorType.BAD_REQUEST,
          "from must not lie after until, and the period must not exceed " + MAX_DAYS + " days");
    }
    // the same two steps as DailyReportRestEndpoint: the contracts covering the period, then their bookings
    return employeecontractService.getEmployeecontractPeriodsBetween(authorizedEmployee.getEmployeeId(), from, until)
        .stream()
        .flatMap(period -> timereportService
            .getTimereportsByDatesAndEmployeeContractId(period.employeecontractId(), period.from(), period.until())
            .stream())
        .map(TimereportView::of)
        .toList();
  }

  /** One call for all bookings of a response, instead of one per booking (concept 6.2). */
  @BatchMapping(typeName = "Timereport")
  Map<TimereportView, SuborderRef> suborder(List<TimereportView> timereports) {
    Set<Long> ids = timereports.stream().map(TimereportView::suborderId).collect(Collectors.toSet());
    Map<Long, SuborderLocation> locations = suborderService.getSuborderLocationsByIds(ids);
    return timereports.stream().collect(Collectors.toMap(Function.identity(),
        timereport -> SuborderRef.of(locations.get(timereport.suborderId())), (a, b) -> a));
  }

  @MutationMapping
  @Authorized
  TimereportPayload createTimereport(@Argument String idempotencyKey, @Argument CreateTimereportInput input) {
    var outcome = idempotency.createOnce("createTimereport", idempotencyKey, input, () -> create(input));
    return new TimereportPayload(read(outcome.id()), outcome.replayed());
  }

  @MutationMapping
  @Authorized
  TimereportPayload updateTimereport(@Argument long id, @Argument UpdateTimereportInput input) {
    TimereportDTO current = readDto(id);
    timereportService.updateTimereport(id, current.getEmployeecontractId(), current.getEmployeeorderId(),
        Objects.requireNonNullElse(input.date(), current.getReferenceday()),
        Objects.requireNonNullElse(input.taskDescription(), current.getTaskdescription()),
        Objects.requireNonNullElse(input.ticketReferences(), current.getTicketReferences()),
        current.isTraining(), current.getSurchargeKind(),
        Objects.requireNonNullElse(input.duration(), current.getDuration()));
    return new TimereportPayload(read(id), false);
  }

  private long create(CreateTimereportInput input) {
    long contractId = employeecontractService
        .getEmployeecontractPeriodsBetween(authorizedEmployee.getEmployeeId(), input.date(), input.date()).stream()
        .findFirst()
        .map(EmployeecontractPeriod::employeecontractId)
        .orElseThrow(() -> new SpikeException(SpikeException.INVALID_INPUT, ErrorType.BAD_REQUEST,
            "No contract on " + input.date()));
    Set<Long> before = idsOn(contractId, input.date());
    timereportService.createTimereports(contractId, input.employeeorderId(), input.date(), input.taskDescription(),
        input.ticketReferences(), false, SurchargeChoice.NONE, input.duration(), 1);
    var created = idsOn(contractId, input.date()).stream().filter(id -> !before.contains(id)).toList();
    if (created.size() != 1) {
      throw new IllegalStateException("Expected one new booking, found " + created.size());
    }
    return created.getFirst();
  }

  private Set<Long> idsOn(long contractId, LocalDate date) {
    return timereportService.getTimereportsForEmployeecontractAndDate(contractId, date).stream()
        .map(TimereportDTO::getId)
        .collect(Collectors.toSet());
  }

  private TimereportView read(long id) {
    return TimereportView.of(readDto(id));
  }

  /** Not found and not readable look the same (concept 6.3). */
  private TimereportDTO readDto(long id) {
    TimereportDTO timereport = timereportService.getTimereportById(id);
    if (timereport == null) {
      throw new SpikeException(SpikeException.NOT_FOUND, ErrorType.NOT_FOUND, "No booking " + id);
    }
    return timereport;
  }

  /** The booking as the schema shows it; equal by id, so that the batch loader can key by it. */
  public record TimereportView(long id, LocalDate date, Duration duration, String taskDescription,
      List<String> ticketReferences, String status, boolean training, long employeeorderId, long suborderId) {

    static TimereportView of(TimereportDTO dto) {
      return new TimereportView(dto.getId(), dto.getReferenceday(), dto.getDuration(), dto.getTaskdescription(),
          dto.getTicketReferences(), dto.getStatus(), dto.isTraining(), dto.getEmployeeorderId(), dto.getSuborderId());
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

  public record SuborderRef(long id, String completeOrderSign) {

    static SuborderRef of(SuborderLocation location) {
      return new SuborderRef(location.id(), location.completeOrderSign());
    }
  }

  public record CreateTimereportInput(long employeeorderId, LocalDate date, Duration duration, String taskDescription,
      List<String> ticketReferences) {
  }

  public record UpdateTimereportInput(LocalDate date, Duration duration, String taskDescription,
      List<String> ticketReferences) {
  }

  public record TimereportPayload(TimereportView timereport, boolean replayed) {
  }
}
