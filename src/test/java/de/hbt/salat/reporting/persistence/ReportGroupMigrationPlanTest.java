package de.hbt.salat.reporting.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.reporting.persistence.ReportGroupMigrationPlan.Placement;
import de.hbt.salat.reporting.persistence.ReportGroupMigrationPlan.Row;
import de.hbt.salat.reporting.persistence.ReportGroupMigrationPlan.Target;

/**
 * Where the existing report definitions go when groups replace the prefix in their name (#1523), on
 * invented examples.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReportGroupMigrationPlanTest {

  private static final long MANAGER = 1L;
  private static final long PEOPLE_LEAD = 2L;
  private static final LocalDateTime EARLY = LocalDateTime.of(2024, 1, 1, 8, 0);
  private static final LocalDateTime LATE = LocalDateTime.of(2025, 1, 1, 8, 0);

  @Test
  void the_prefix_becomes_a_group_and_leaves_the_title() {
    var plan = ReportGroupMigrationPlan.plan(List.of(row(1, "Monatsabschluss: Offene Freigaben", MANAGER, false)));

    assertThat(plan.groupNames()).containsExactly("Monatsabschluss");
    assertThat(plan.placements()).extracting(Placement::title, Placement::target)
        .containsExactly(tuple("Offene Freigaben", new Target("Monatsabschluss", null)));
  }

  @Test
  void only_the_first_colon_separates_and_the_blanks_after_it_go() {
    var plan = ReportGroupMigrationPlan.plan(List.of(row(1, "Personal:   Stunden 08:00 bis 17:00", MANAGER, false)));

    assertThat(plan.placements()).extracting(Placement::title).containsExactly("Stunden 08:00 bis 17:00");
  }

  @Test
  void a_name_without_a_prefix_stays_as_it_is_and_without_a_group() {
    var plan = ReportGroupMigrationPlan.plan(List.of(
        row(1, "Umsatz", MANAGER, false),
        row(2, ": nichts davor", MANAGER, false),
        row(3, "nichts danach:", null, false)));

    assertThat(plan.groupNames()).isEmpty();
    assertThat(plan.placements()).extracting(Placement::title, placement -> placement.target().isNone())
        .containsExactly(tuple("Umsatz", true), tuple(": nichts davor", true), tuple("nichts danach:", true));
  }

  /** The prefix of a people lead is often a sign of their own; the personal group takes its place. */
  @Test
  void a_people_lead_s_report_goes_into_the_personal_group_with_or_without_prefix() {
    var plan = ReportGroupMigrationPlan.plan(List.of(
        row(1, "ab: Überstunden Team", PEOPLE_LEAD, true),
        row(2, "Urlaub Team", PEOPLE_LEAD, true),
        row(3, "Monatsabschluss: Team", PEOPLE_LEAD, true)));

    assertThat(plan.groupNames()).isEmpty();
    assertThat(plan.personalOwners()).containsExactly(PEOPLE_LEAD);
    assertThat(plan.placements()).extracting(Placement::title, Placement::target).containsExactly(
        tuple("Überstunden Team", new Target(null, PEOPLE_LEAD)),
        tuple("Urlaub Team", new Target(null, PEOPLE_LEAD)),
        tuple("Team", new Target(null, PEOPLE_LEAD)));
  }

  @Test
  void prefixes_differing_in_case_are_one_group_spelled_as_most_reports_spell_it() {
    var plan = ReportGroupMigrationPlan.plan(List.of(
        row(1, "personal: A", MANAGER, false, EARLY),
        row(2, "Personal: B", MANAGER, false, LATE),
        row(3, "Personal: C", MANAGER, false, LATE)));

    assertThat(plan.groupNames()).containsExactly("Personal");
    assertThat(plan.placements()).extracting(Placement::target).containsOnly(new Target("Personal", null));
  }

  @Test
  void on_a_tie_the_oldest_report_decides_the_spelling() {
    var plan = ReportGroupMigrationPlan.plan(List.of(
        row(1, "CONTROLLING: A", MANAGER, false, LATE),
        row(2, "Controlling: B", MANAGER, false, EARLY)));

    assertThat(plan.groupNames()).containsExactly("Controlling");
  }

  @Test
  void where_two_titles_of_a_group_collide_the_younger_report_keeps_its_name() {
    var plan = ReportGroupMigrationPlan.plan(List.of(
        row(1, "Personal: Urlaub", MANAGER, false, LATE),
        row(2, "personal: urlaub", MANAGER, false, EARLY)));

    assertThat(plan.placements()).extracting(Placement::id, Placement::title)
        .containsExactly(tuple(1L, "Personal: Urlaub"), tuple(2L, "urlaub"));
  }

  @Test
  void a_collision_in_a_personal_group_is_resolved_the_same_way() {
    var plan = ReportGroupMigrationPlan.plan(List.of(
        row(1, "Urlaub Team", PEOPLE_LEAD, true, EARLY),
        row(2, "ab: Urlaub Team", PEOPLE_LEAD, true, LATE)));

    assertThat(plan.placements()).extracting(Placement::id, Placement::title)
        .containsExactly(tuple(1L, "Urlaub Team"), tuple(2L, "ab: Urlaub Team"));
  }

  @Test
  void the_same_title_in_different_groups_is_no_collision() {
    var plan = ReportGroupMigrationPlan.plan(List.of(
        row(1, "Personal: Übersicht", MANAGER, false),
        row(2, "Controlling: Übersicht", MANAGER, false),
        row(3, "Übersicht", MANAGER, false)));

    assertThat(plan.placements()).extracting(Placement::title).containsOnly("Übersicht");
  }

  @Test
  void a_report_without_an_owner_is_grouped_by_its_prefix() {
    var plan = ReportGroupMigrationPlan.plan(List.of(row(1, "Personal: A", null, false)));

    assertThat(plan.placements()).extracting(Placement::target).containsExactly(new Target("Personal", null));
  }

  private static Row row(long id, String name, Long owner, boolean peopleLead) {
    return row(id, name, owner, peopleLead, EARLY.plusDays(id));
  }

  private static Row row(long id, String name, Long owner, boolean peopleLead, LocalDateTime created) {
    return new Row(id, name, owner, peopleLead, created);
  }
}
