package de.hbt.salat.common.scheduling;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import de.hbt.salat.common.SalatProperties;

/**
 * Writing the outcome of a run while the database is briefly unreachable (#1300).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class RunFinisherTest {

  private static final Instant START = Instant.parse("2026-10-03T00:01:00Z");
  private static final Duration MAX = Duration.ofMinutes(60);

  private final ManualTaskScheduler taskScheduler = new ManualTaskScheduler(START);
  private final RunFinisher runFinisher = taskScheduler.runFinisher(MAX);

  private final ListAppender<ILoggingEvent> log = new ListAppender<>();

  @BeforeEach
  void captureLog() {
    log.start();
    ((Logger) LoggerFactory.getLogger(RunFinisher.class)).addAppender(log);
  }

  @AfterEach
  void releaseLog() {
    ((Logger) LoggerFactory.getLogger(RunFinisher.class)).detachAppender(log);
  }

  @Test
  void an_outcome_that_can_be_written_is_written_at_once_and_nothing_is_planned() {
    var written = new AtomicInteger();

    runFinisher.finish("ETL run 7", written::incrementAndGet);

    assertThat(written).hasValue(1);
    assertThat(taskScheduler.plannedTimes()).isEmpty();
  }

  @Test
  void a_connection_error_is_retried_with_a_growing_delay_until_the_write_goes_through() {
    var attempts = new ArrayList<Instant>();
    runFinisher.finish("ETL run 7", failingTimes(4, attempts));

    int retries = taskScheduler.runAllPlanned();

    assertThat(retries).isEqualTo(4);
    assertThat(attempts).containsExactly(
        START,
        START.plusSeconds(5),
        START.plusSeconds(5 + 10),
        START.plusSeconds(5 + 10 + 20),
        START.plusSeconds(5 + 10 + 20 + 40));
  }

  @Test
  void the_delay_between_two_attempts_does_not_grow_beyond_five_minutes() {
    var attempts = new ArrayList<Instant>();
    runFinisher.finish("ETL run 7", failingTimes(9, attempts));

    taskScheduler.runAllPlanned();

    var delays = new ArrayList<Duration>();
    for (int i = 1; i < attempts.size(); i++) {
      delays.add(Duration.between(attempts.get(i - 1), attempts.get(i)));
    }
    assertThat(delays).containsExactly(
        Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(20), Duration.ofSeconds(40),
        Duration.ofSeconds(80), Duration.ofSeconds(160), Duration.ofMinutes(5), Duration.ofMinutes(5),
        Duration.ofMinutes(5));
  }

  @Test
  void the_retry_ends_at_the_configured_threshold_and_names_the_run_in_the_log() {
    var attempts = new ArrayList<Instant>();
    runFinisher.finish("ETL run 7", failingTimes(Integer.MAX_VALUE, attempts));

    taskScheduler.runAllPlanned();

    assertThat(attempts.getLast()).isEqualTo(START.plus(MAX));
    assertThat(attempts).allSatisfy(attempt -> assertThat(attempt).isBeforeOrEqualTo(START.plus(MAX)));
    assertThat(taskScheduler.plannedTimes()).isEmpty();
    assertThat(log.list)
        .filteredOn(event -> event.getLevel() == Level.ERROR)
        .singleElement()
        .satisfies(event -> assertThat(event.getFormattedMessage())
            .contains("ETL run 7")
            .contains("marked finished by hand"));
  }

  @Test
  void every_kind_of_connection_error_is_retried() {
    var errors = List.<RuntimeException>of(
        new CannotCreateTransactionException("Connection is closed"),
        new DataAccessResourceFailureException("Server shutdown in progress"),
        new QueryTimeoutException("timeout"));
    var remaining = new ArrayList<>(errors);
    var written = new AtomicInteger();

    runFinisher.finish("JIRA replication run 11", () -> {
      if (!remaining.isEmpty()) {
        throw remaining.removeFirst();
      }
      written.incrementAndGet();
    });
    taskScheduler.runAllPlanned();

    assertThat(written).hasValue(1);
  }

  @Test
  void any_other_error_reaches_the_caller_and_is_not_retried() {
    assertThatThrownBy(() -> runFinisher.finish("ETL run 7", () -> {
      throw new DataIntegrityViolationException("message too long");
    })).isInstanceOf(DataIntegrityViolationException.class);

    assertThat(taskScheduler.plannedTimes()).isEmpty();
  }

  @Test
  void an_error_other_than_a_connection_error_in_a_retry_ends_it_with_the_run_in_the_log() {
    var attempts = new AtomicInteger();
    runFinisher.finish("ETL run 7", () -> {
      if (attempts.incrementAndGet() == 1) {
        throw new CannotCreateTransactionException("Connection is closed");
      }
      throw new IllegalStateException("unexpected");
    });

    taskScheduler.runAllPlanned();

    assertThat(attempts).hasValue(2);
    assertThat(log.list)
        .filteredOn(event -> event.getLevel() == Level.ERROR)
        .singleElement()
        .satisfies(event -> assertThat(event.getFormattedMessage()).contains("ETL run 7"));
  }

  /**
   * The scheduler's pool has a single thread, shared by every {@code @Scheduled} job. While a run
   * waits for its next attempt, that thread must stay free: a job due meanwhile runs at once, and
   * {@code finish} itself returns long before the first retry is due.
   */
  @Test
  void while_a_retry_is_pending_no_thread_is_taken_and_other_jobs_run() throws InterruptedException {
    var scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.initialize();
    try {
      var properties = new SalatProperties();
      properties.getRuns().setFinishRetryMax(MAX);
      var finisher = new RunFinisher(scheduler, mock(PlatformTransactionManager.class), properties);

      long before = System.nanoTime();
      finisher.finish("ETL run 7", () -> {
        throw new CannotCreateTransactionException("Connection is closed");
      });
      var returnedAfter = Duration.ofNanos(System.nanoTime() - before);

      var otherJob = new CountDownLatch(1);
      scheduler.execute(otherJob::countDown);

      assertThat(returnedAfter).isLessThan(RunFinisher.FIRST_RETRY_DELAY);
      assertThat(otherJob.await(2, SECONDS)).isTrue();
      assertThat(scheduler.getScheduledThreadPoolExecutor().getQueue()).hasSize(1);
    } finally {
      scheduler.shutdown();
    }
  }

  /** The threshold is one entry for both runs, and its value stands in the configuration, not in the code. */
  @Test
  void the_threshold_is_sixty_minutes_in_application_yaml() throws IOException {
    var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"));
    var binder = new Binder(ConfigurationPropertySources.from(sources));

    assertThat(binder.bind("salat.runs.finish-retry-max", Duration.class).get()).isEqualTo(Duration.ofMinutes(60));
  }

  /** A write that fails with a connection error the given number of times, then goes through. */
  private Runnable failingTimes(int failures, List<Instant> attempts) {
    var count = new AtomicInteger();
    return () -> {
      attempts.add(taskScheduler.getClock().instant());
      if (count.incrementAndGet() <= failures) {
        throw new CannotCreateTransactionException("Connection is closed");
      }
    };
  }

}
