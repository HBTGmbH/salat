package org.tb.common.test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.tb.common.GlobalConstants;
import org.tb.common.util.ClockProvider;

/**
 * JUnit 5 extension that pins {@link ClockProvider} to a fixed instant before each test and
 * resets it to the live system clock afterwards.
 *
 * <p>The fixture is taken from a {@link FixedClock} annotation on the test method (preferred)
 * or the test class, including one the class inherits from a superclass; if the extension is
 * registered directly via {@code @ExtendWith(FixedClockExtension.class)} without a
 * {@link FixedClock} annotation, the default fixture {@link #DEFAULT_FIXTURE} is used.
 *
 * <p>Registered on the class, the extension also pins the clock to the class's day before the
 * {@code @BeforeAll} methods run, so a fixture set up there sees the same day as the tests.
 * Between two tests and after the last one the live clock is back.
 *
 * <p>A test that moves the clock itself via {@link ClockProvider#setClock(Clock)} need not put it
 * back: the extension resets it after the test.
 */
public class FixedClockExtension implements BeforeAllCallback, AfterAllCallback,
    BeforeEachCallback, AfterEachCallback {

  static final String DEFAULT_FIXTURE = "2026-06-25T10:15:30";

  @Override
  public void beforeAll(ExtensionContext context) {
    useFixture(classFixture(context));
  }

  @Override
  public void afterAll(ExtensionContext context) {
    ClockProvider.reset();
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    useFixture(resolveFixture(context));
  }

  @Override
  public void afterEach(ExtensionContext context) {
    ClockProvider.reset();
  }

  private static void useFixture(String fixture) {
    ZoneId zone = ZoneId.of(GlobalConstants.DEFAULT_TIMEZONE_ID);
    ClockProvider.setClock(Clock.fixed(LocalDateTime.parse(fixture).atZone(zone).toInstant(), zone));
  }

  private static String resolveFixture(ExtensionContext context) {
    Optional<FixedClock> onMethod = context.getTestMethod()
        .map(method -> method.getAnnotation(FixedClock.class))
        .filter(Objects::nonNull);
    if (onMethod.isPresent()) {
      return onMethod.get().value();
    }
    return classFixture(context);
  }

  private static String classFixture(ExtensionContext context) {
    return context.getTestClass()
        .map(clazz -> clazz.getAnnotation(FixedClock.class))
        .filter(Objects::nonNull)
        .map(FixedClock::value)
        .orElse(DEFAULT_FIXTURE);
  }
}
