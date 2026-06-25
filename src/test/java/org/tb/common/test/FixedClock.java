package org.tb.common.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Pins {@link org.tb.common.util.ClockProvider} to a fixed instant for the duration of each
 * test, so that {@code DateTimeUtils.now()} / {@code DateUtils.today()} (and everything that
 * funnels through them) return a deterministic value. The clock is restored to live system
 * time after each test.
 *
 * <p>Place on a test class (or a single test method). The {@link #value()} is an
 * ISO-8601 local date-time (e.g. {@code 2026-06-25T10:15:30}); the default matches the dates
 * commonly hard-coded in existing fixtures.
 *
 * <pre>
 * &#64;FixedClock
 * class MyTest { ... }                       // now() == 2026-06-25T10:15:30
 *
 * &#64;FixedClock("2030-01-01T00:00:00")
 * class OtherTest { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtendWith(FixedClockExtension.class)
public @interface FixedClock {

  String value() default "2026-06-25T10:15:30";
}
