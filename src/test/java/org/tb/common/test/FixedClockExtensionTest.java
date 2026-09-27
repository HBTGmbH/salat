package org.tb.common.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.tb.common.util.ClockProvider;

class FixedClockExtensionTest {

  @FixedClock("2030-01-01T08:00:00")
  abstract static class AnnotatedBase {
  }

  static class SubclassWithoutOwnClock extends AnnotatedBase {
  }

  @FixedClock("2031-02-02T09:00:00")
  static class SubclassWithOwnClock extends AnnotatedBase {

    @FixedClock("2032-03-03T10:00:00")
    void methodWithOwnClock() {
    }

    void methodWithoutOwnClock() {
    }
  }

  private final FixedClockExtension extension = new FixedClockExtension();

  @AfterEach
  void resetClock() {
    ClockProvider.reset();
  }

  @Test
  void subclass_without_own_annotation_gets_the_day_of_its_superclass() {
    extension.beforeEach(context(SubclassWithoutOwnClock.class, null));

    assertThat(ClockProvider.now()).isEqualTo(LocalDateTime.parse("2030-01-01T08:00:00"));
  }

  @Test
  void own_annotation_of_the_subclass_wins_over_the_superclass() throws NoSuchMethodException {
    Method method = SubclassWithOwnClock.class.getDeclaredMethod("methodWithoutOwnClock");
    extension.beforeEach(context(SubclassWithOwnClock.class, method));

    assertThat(ClockProvider.now()).isEqualTo(LocalDateTime.parse("2031-02-02T09:00:00"));
  }

  @Test
  void annotation_on_the_method_wins_over_the_class() throws NoSuchMethodException {
    Method method = SubclassWithOwnClock.class.getDeclaredMethod("methodWithOwnClock");
    extension.beforeEach(context(SubclassWithOwnClock.class, method));

    assertThat(ClockProvider.now()).isEqualTo(LocalDateTime.parse("2032-03-03T10:00:00"));
  }

  @Test
  void before_all_pins_the_day_of_the_class_for_the_before_all_methods() {
    extension.beforeAll(context(SubclassWithoutOwnClock.class, null));

    assertThat(ClockProvider.now()).isEqualTo(LocalDateTime.parse("2030-01-01T08:00:00"));
  }

  private static ExtensionContext context(Class<?> testClass, Method testMethod) {
    ExtensionContext context = mock(ExtensionContext.class);
    when(context.getTestClass()).thenReturn(Optional.of(testClass));
    when(context.getTestMethod()).thenReturn(Optional.ofNullable(testMethod));
    return context;
  }
}
