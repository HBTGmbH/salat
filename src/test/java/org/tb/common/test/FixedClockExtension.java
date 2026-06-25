package org.tb.common.test;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.tb.common.util.ClockProvider;

/**
 * JUnit 5 extension that pins {@link ClockProvider} to a fixed instant before each test and
 * resets it to the live system clock afterwards.
 *
 * <p>The fixture is taken from a {@link FixedClock} annotation on the test method (preferred)
 * or the test class; if the extension is registered directly via
 * {@code @ExtendWith(FixedClockExtension.class)} without a {@link FixedClock} annotation, the
 * default fixture {@link #DEFAULT_FIXTURE} is used.
 */
public class FixedClockExtension implements BeforeEachCallback, AfterEachCallback {

  static final String DEFAULT_FIXTURE = "2026-06-25T10:15:30";

  @Override
  public void beforeEach(ExtensionContext context) {
    ClockProvider.useFixedClock(LocalDateTime.parse(resolveFixture(context)));
  }

  @Override
  public void afterEach(ExtensionContext context) {
    ClockProvider.reset();
  }

  private String resolveFixture(ExtensionContext context) {
    Optional<FixedClock> onMethod = context.getTestMethod()
        .map(method -> method.getAnnotation(FixedClock.class))
        .filter(Objects::nonNull);
    if (onMethod.isPresent()) {
      return onMethod.get().value();
    }
    return context.getTestClass()
        .map(clazz -> clazz.getAnnotation(FixedClock.class))
        .filter(Objects::nonNull)
        .map(FixedClock::value)
        .orElse(DEFAULT_FIXTURE);
  }
}
