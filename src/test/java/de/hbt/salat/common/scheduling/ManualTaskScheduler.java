package de.hbt.salat.common.scheduling;

import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.transaction.PlatformTransactionManager;
import de.hbt.salat.common.SalatProperties;

/**
 * A {@link TaskScheduler} for tests that runs nothing on its own: planned tasks wait until the test
 * runs them, and the clock jumps to the time each one was planned for. One-shot tasks only — the
 * recurring variants are not needed by {@link RunFinisher} and refuse.
 */
public class ManualTaskScheduler implements TaskScheduler {

  private record Planned(Runnable task, Instant at) {}

  private final List<Planned> planned = new ArrayList<>();
  private Instant now;

  public ManualTaskScheduler(Instant start) {
    this.now = start;
  }

  @Override
  public Clock getClock() {
    return Clock.fixed(now, ZoneOffset.UTC);
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
    planned.add(new Planned(task, startTime));
    return null;
  }

  /**
   * A {@link RunFinisher} that plans its retries here. Its transaction manager does nothing; a test
   * stands for a connection error by letting the write throw.
   */
  public RunFinisher runFinisher(Duration finishRetryMax) {
    var properties = new SalatProperties();
    properties.getRuns().setFinishRetryMax(finishRetryMax);
    return new RunFinisher(this, mock(PlatformTransactionManager.class), properties);
  }

  /** The times the planned tasks are due at, in the order they were planned. */
  public List<Instant> plannedTimes() {
    return planned.stream().map(Planned::at).toList();
  }

  /** Runs the task due next, after moving the clock to its time. */
  public void runNext() {
    var next = planned.stream().min(Comparator.comparing(Planned::at)).orElseThrow();
    planned.remove(next);
    now = next.at();
    next.task().run();
  }

  /** Runs planned tasks — including those they plan in turn — until none is left. */
  public int runAllPlanned() {
    int count = 0;
    while (!planned.isEmpty()) {
      runNext();
      count++;
    }
    return count;
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
    throw new UnsupportedOperationException();
  }

}
