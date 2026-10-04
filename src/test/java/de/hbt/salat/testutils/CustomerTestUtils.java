package de.hbt.salat.testutils;

import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CustomerTestUtils {

  private static final AtomicInteger NEXT = new AtomicInteger();

  /**
   * A customer short name no other test data carries — the unique key on {@code shortname} refuses a
   * second one (#1333), and the test database is shared by every class that commits.
   */
  public static String uniqueShortname(String prefix) {
    return prefix + NEXT.incrementAndGet();
  }
}
