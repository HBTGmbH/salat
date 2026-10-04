package de.hbt.salat.testutils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.experimental.UtilityClass;
import de.hbt.salat.budget.domain.CostCategory;
import de.hbt.salat.common.domain.AuditedEntity;

@UtilityClass
public class CostCategoryTestUtils {

  private static final AtomicLong NEXT_ID = new AtomicLong(900_000);
  private static final Map<String, Long> IDS = new ConcurrentHashMap<>();

  /**
   * A cost category as a stored one looks, without a database (#1209): the same name always gets the
   * same id, so rate periods and assignments of a fixture that name the same category meet over its
   * id, as they do in production.
   */
  public static CostCategory named(String name) {
    var category = new CostCategory(name);
    setId(category, IDS.computeIfAbsent(name, n -> NEXT_ID.incrementAndGet()));
    return category;
  }

  private static void setId(AuditedEntity entity, long id) {
    try {
      Field field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
  }
}
