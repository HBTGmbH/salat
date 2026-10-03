package de.hbt.salat.dailyreport.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeJars;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * The calendar columns of a reference day follow the date and {@code publicholiday} (#1211). A holiday is an entry in
 * {@code publicholiday}, nothing else; a weekend is no holiday, it is no working day.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReferencedayCalendarTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
  private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 10);

  @Test
  void a_weekday_without_a_holiday_is_a_working_day() {
    var day = on(MONDAY, null);

    assertThat(day.getHoliday()).isFalse();
    assertThat(day.getName()).isEmpty();
    assertThat(day.getWorkingday()).isTrue();
    assertThat(day.getDow()).isEqualTo("Mon");
  }

  @Test
  void a_holiday_on_a_weekday_is_no_working_day_and_carries_its_name() {
    var day = on(MONDAY, "Feiertag");

    assertThat(day.getHoliday()).isTrue();
    assertThat(day.getName()).isEqualTo("Feiertag");
    assertThat(day.getWorkingday()).isFalse();
  }

  /** Up to 2024 the stock said otherwise; changeset 115 aligned it. */
  @Test
  void a_weekend_is_no_holiday() {
    var day = on(SATURDAY, null);

    assertThat(day.getHoliday()).isFalse();
    assertThat(day.getWorkingday()).isFalse();
    assertThat(day.getDow()).isEqualTo("Sat");
  }

  @Test
  void a_holiday_on_a_weekend_is_a_holiday_all_the_same() {
    var day = on(SATURDAY, "Feiertag");

    assertThat(day.getHoliday()).isTrue();
    assertThat(day.getWorkingday()).isFalse();
  }

  @Test
  void a_correction_of_the_holiday_is_taken_over_completely() {
    var day = on(MONDAY, "Feiertag");

    day.applyCalendar(null);

    assertThat(day.getHoliday()).isFalse();
    assertThat(day.getName()).isEmpty();
    assertThat(day.getWorkingday()).isTrue();
  }

  /**
   * Why aligning the stock leaves closed balances alone: the working time target and the overtime are computed from
   * the date and {@code publicholiday}, and nothing in the application reads the derived columns. Should anybody
   * start to, an old balance would move with every correction of a holiday — this rule says so first.
   */
  @Test
  void nothing_but_the_reference_day_itself_reads_its_calendar_columns() {
    var classes = new ClassFileImporter()
        .withImportOption(new DoNotIncludeTests())
        .withImportOption(new DoNotIncludeJars())
        .importPackages("de.hbt.salat");

    noClasses().that().doNotBelongToAnyOf(Referenceday.class)
        .should().callMethod(Referenceday.class, "getHoliday")
        .orShould().callMethod(Referenceday.class, "getWorkingday")
        .orShould().callMethod(Referenceday.class, "getName")
        .orShould().callMethod(Referenceday.class, "getDow")
        .check(classes);
  }

  private static Referenceday on(LocalDate date, String holidayName) {
    var day = new Referenceday();
    day.setRefdate(date);
    day.applyCalendar(holidayName);
    return day;
  }
}
