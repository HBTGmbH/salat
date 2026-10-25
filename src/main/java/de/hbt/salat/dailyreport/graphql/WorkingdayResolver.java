package de.hbt.salat.dailyreport.graphql;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;

/**
 * The own working day over GraphQL (concept stage 2): a booking needs a start of work on its day ({@code TR-0024}),
 * so creating bookings without it would leave the caller stuck on every new day. Writes through
 * {@link WorkingdayService}, with the rules of the daily view.
 */
@Slf4j
@Controller
@Authorized
@RequiredArgsConstructor
class WorkingdayResolver {

  private final WorkingdayService workingdayService;
  private final AuthorizedEmployee authorizedEmployee;
  private final OwnContract ownContract;

  @QueryMapping
  @Authorized
  WorkingdayView workingday(@Argument LocalDate date) {
    return read(ownContract.on(date), date).orElse(null);
  }

  @MutationMapping
  @Authorized
  WorkingdayView upsertWorkingday(@Argument WorkingdayInput input) {
    long contractId = ownContract.on(input.date());
    workingdayService.upsertWorkingday(contractId, input.date(), input.startTime(),
        Objects.requireNonNullElse(input.breakLength(), Duration.ZERO), input.type());
    log.info("GraphQL upsertWorkingday {} by {}", input.date(), authorizedEmployee.getSign());
    return read(contractId, input.date()).orElseThrow();
  }

  @MutationMapping
  @Authorized
  WorkingdayView markNotWorked(@Argument LocalDate date) {
    long contractId = ownContract.on(date);
    workingdayService.markNotWorked(contractId, date);
    log.info("GraphQL markNotWorked {} by {}", date, authorizedEmployee.getSign());
    return read(contractId, date).orElseThrow();
  }

  private Optional<WorkingdayView> read(long contractId, LocalDate date) {
    return Optional.ofNullable(workingdayService.getWorkingday(contractId, date)).map(WorkingdayView::of);
  }

  public record WorkingdayView(LocalDate date, LocalTime startTime, Duration breakLength, WorkingDayType type) {

    static WorkingdayView of(Workingday workingday) {
      return new WorkingdayView(workingday.getRefday(), workingday.getStartTime(), workingday.getBreakLength(),
          workingday.getType());
    }
  }

  public record WorkingdayInput(LocalDate date, LocalTime startTime, Duration breakLength, WorkingDayType type) {
  }
}
