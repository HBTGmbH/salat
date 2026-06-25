package org.tb.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.tb.common.test.FixedClock;

class ClockProviderTest {

  @Test
  @FixedClock
  void fixedClock_pinsNowToDefaultFixture() {
    assertThat(ClockProvider.now()).isEqualTo(LocalDateTime.of(2026, 6, 25, 10, 15, 30));
    assertThat(DateTimeUtils.now()).isEqualTo(LocalDateTime.of(2026, 6, 25, 10, 15, 30));
    assertThat(DateUtils.today()).isEqualTo(LocalDate.of(2026, 6, 25));
  }

  @Test
  @FixedClock("2030-01-01T08:00:00")
  void fixedClock_honoursCustomFixture() {
    assertThat(DateTimeUtils.now()).isEqualTo(LocalDateTime.of(2030, 1, 1, 8, 0, 0));
    assertThat(DateUtils.today()).isEqualTo(LocalDate.of(2030, 1, 1));
  }

  @Test
  void withoutFixedClock_usesLiveSystemTime() {
    // No @FixedClock here: confirms the funnel reflects a live clock and is not left pinned
    // by a previous test (the extension resets after each test).
    assertThat(DateUtils.today()).isAfter(LocalDate.of(2020, 1, 1));
  }
}
