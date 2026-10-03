package de.hbt.salat.testutils;

import java.time.LocalDate;
import lombok.experimental.UtilityClass;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import de.hbt.salat.dailyreport.domain.Referenceday;

@UtilityClass
public class ReferencedayTestUtils {

  /**
   * One row per date, as in production — the reference day is shared by every booking of that day, and the unique
   * key on {@code refdate} refuses a second one (#1208).
   */
  public static Referenceday referenceday(TestEntityManager entityManager, LocalDate day) {
    var existing = entityManager.getEntityManager()
        .createQuery("select r from Referenceday r where r.refdate = :day", Referenceday.class)
        .setParameter("day", day)
        .getResultList();
    if (!existing.isEmpty()) {
      return existing.getFirst();
    }
    var referenceday = new Referenceday();
    referenceday.setRefdate(day);
    return entityManager.persist(referenceday);
  }
}
