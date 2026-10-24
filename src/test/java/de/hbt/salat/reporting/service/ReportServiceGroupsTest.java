package de.hbt.salat.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.auth.domain.AccessLevel.EXECUTE;
import static de.hbt.salat.auth.domain.AccessLevel.WRITE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.reporting.auth.ReportAuthorization;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.domain.ReportGroup;
import de.hbt.salat.reporting.domain.ReportGroupInfo;
import de.hbt.salat.reporting.persistence.OwnerReferences;
import de.hbt.salat.reporting.persistence.ReportDefinitionRepository;
import de.hbt.salat.reporting.persistence.ReportGroupRepository;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Which report groups a person sees and which one their report goes into (#1523), per role: the
 * manager, the people lead who is not a manager, the person who may run a report through an
 * authorization rule, and the person without any. {@link ReportAuthorization} is mocked: whom it lets
 * run which report is tested in {@code ReportAuthorizationTest}; here it is the given.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReportServiceGroupsTest {

  private static final long LEAD_USER_ID = 7L;
  private static final long OTHER_LEAD_USER_ID = 8L;

  @Mock private ReportDefinitionRepository reportDefinitionRepository;
  @Mock private ReportGroupRepository reportGroupRepository;
  @Mock private ReportAuthorization reportAuthorization;
  @Mock private AuthorizedUser authorizedUser;
  @Mock private OwnerReferences ownerReferences;
  @Mock private EmployeeService employeeService;

  private ReportService reportService;

  private final ReportGroup controlling = group(1L, "Controlling");
  private final ReportGroup monatsabschluss = group(2L, "Monatsabschluss");
  private final ReportGroup leadsGroup = personalGroup(3L, LEAD_USER_ID);
  private final ReportGroup otherLeadsGroup = personalGroup(4L, OTHER_LEAD_USER_ID);

  private final ReportDefinition umsatz = report(10L, "Umsatz", controlling);
  private final ReportDefinition auslastung = report(11L, "Auslastung", controlling);
  private final ReportDefinition freigaben = report(12L, "Offene Freigaben", monatsabschluss);
  private final ReportDefinition team = report(13L, "Überstunden Team", leadsGroup);
  private final ReportDefinition otherTeam = report(14L, "Urlaub Team", otherLeadsGroup);
  private final ReportDefinition loose = report(15L, "Buchungen je Woche", null);

  @BeforeEach
  void setUp() {
    reportService = new ReportService(reportDefinitionRepository, null, reportAuthorization, authorizedUser,
        ownerReferences, reportGroupRepository, new ReportGroupNames(employeeService));
    when(reportDefinitionRepository.findAllWithGroup())
        .thenReturn(List.of(loose, auslastung, freigaben, team, umsatz, otherTeam));
    when(employeeService.getNamesBySalatUserIds(any())).thenReturn(Map.of(
        LEAD_USER_ID, "Person A", OTHER_LEAD_USER_ID, "Person B"));
    when(authorizedUser.getEffectiveUserId()).thenReturn(LEAD_USER_ID);
  }

  @Test
  void the_manager_sees_every_group_in_order_with_the_reports_without_one_last() {
    mayExecute(loose, auslastung, freigaben, team, umsatz, otherTeam);

    var overview = reportService.getOverview(null, null);

    assertThat(overview.visibleGroups()).extracting(ReportGroupInfo::name)
        .containsExactly("Controlling", "Monatsabschluss", "Person A", "Person B", null);
    assertThat(overview.groups()).extracting(group -> group.group().name(), group -> group.reports().size())
        .containsExactly(
            tuple("Controlling", 2),
            tuple("Monatsabschluss", 1),
            tuple("Person A", 1),
            tuple("Person B", 1),
            tuple(null, 1));
    assertThat(overview.groups().get(2).group().personal()).isTrue();
  }

  /** A group is seen by whoever may run a report in it, and it counts only those. */
  @Test
  void a_person_with_a_rule_sees_only_the_groups_of_the_reports_they_may_run() {
    mayExecute(umsatz, loose);

    var overview = reportService.getOverview(null, null);

    assertThat(overview.visibleGroups()).extracting(ReportGroupInfo::name).containsExactly("Controlling", null);
    assertThat(overview.groups()).extracting(group -> group.reports().size()).containsExactly(1, 1);
  }

  @Test
  void a_person_without_any_report_sees_no_group() {
    var overview = reportService.getOverview(null, null);

    assertThat(overview.visibleGroups()).isEmpty();
    assertThat(overview.groups()).isEmpty();
  }

  @Test
  void the_free_text_finds_a_report_by_the_name_of_its_group_too() {
    mayExecute(loose, auslastung, freigaben, team, umsatz, otherTeam);

    assertThat(reportService.getOverview("monatsab", null).groups())
        .flatExtracting(group -> group.reports()).containsExactly(freigaben);
    assertThat(reportService.getOverview("person a", null).groups())
        .flatExtracting(group -> group.reports()).containsExactly(team);
    assertThat(reportService.getOverview("um", null).groups())
        .flatExtracting(group -> group.reports()).containsExactly(umsatz);
  }

  @Test
  void the_group_filter_shows_one_group_and_leaves_the_choice_of_groups_alone() {
    mayExecute(loose, auslastung, freigaben, team, umsatz, otherTeam);

    var overview = reportService.getOverview(null, controlling.getId());

    assertThat(overview.groups()).singleElement().satisfies(group ->
        assertThat(group.reports()).containsExactly(auslastung, umsatz));
    assertThat(overview.visibleGroups()).hasSize(5);
    assertThat(reportService.getOverview(null, ReportService.WITHOUT_GROUP).groups()).singleElement()
        .satisfies(group -> assertThat(group.group().isNone()).isTrue());
  }

  @Test
  void a_manager_chooses_the_group_freely_also_a_personal_one_or_none() {
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.isPeopleLead()).thenReturn(true);
    when(reportGroupRepository.findById(otherLeadsGroup.getId())).thenReturn(Optional.of(otherLeadsGroup));

    assertThat(reportService.getTargetGroup(otherLeadsGroup.getId(), null).name()).isEqualTo("Person B");
    assertThat(reportService.getTargetGroup(null, null).isNone()).isTrue();
  }

  @Test
  void a_people_lead_has_no_choice_their_report_goes_into_their_personal_group() {
    actingAsPeopleLead();
    when(reportGroupRepository.findByOwnerUserId(LEAD_USER_ID)).thenReturn(Optional.of(leadsGroup));

    var target = reportService.getTargetGroup(controlling.getId(), null);

    assertThat(target.id()).isEqualTo(leadsGroup.getId());
    assertThat(target.name()).isEqualTo("Person A");
  }

  /** Before their first report the group does not exist, and is named all the same. */
  @Test
  void a_people_lead_without_a_group_yet_sees_it_named() {
    actingAsPeopleLead();
    when(reportGroupRepository.findByOwnerUserId(LEAD_USER_ID)).thenReturn(Optional.empty());
    when(employeeService.getNamesBySalatUserIds(Set.of(LEAD_USER_ID))).thenReturn(Map.of(LEAD_USER_ID, "Person A"));

    var target = reportService.getTargetGroup(null, null);

    assertThat(target.personal()).isTrue();
    assertThat(target.name()).isEqualTo("Person A");
    assertThat(target.isNone()).isFalse();
  }

  @Test
  void a_people_lead_gets_the_personal_group_with_the_first_report_and_only_once() {
    actingAsPeopleLead();
    when(reportAuthorization.isAuthorizedForAnyReportDefinition(any())).thenReturn(true);
    when(reportGroupRepository.findByOwnerUserId(LEAD_USER_ID)).thenReturn(Optional.empty());
    when(reportGroupRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var lead = new SalatUser();
    ReflectionTestUtils.setField(lead, "id", LEAD_USER_ID);
    when(ownerReferences.salatUser(LEAD_USER_ID)).thenReturn(lead);

    var created = reportService.create("Überstunden Team", "select 1", controlling.getId());

    assertThat(created.getGroup().isPersonal()).isTrue();
    verify(reportGroupRepository, times(1)).save(any());

    when(reportGroupRepository.findByOwnerUserId(LEAD_USER_ID)).thenReturn(Optional.of(leadsGroup));
    var second = reportService.create("Urlaub Team", "select 1", null);

    assertThat(second.getGroup()).isSameAs(leadsGroup);
    verify(reportGroupRepository, times(1)).save(any());
  }

  /** Bearbeitet ein Manager den Report eines People Leads, bleibt die Gruppe, bis er sie ändert. */
  @Test
  void a_manager_editing_a_people_lead_s_report_keeps_the_group_the_form_sends() {
    when(authorizedUser.isManager()).thenReturn(true);
    when(reportAuthorization.isAuthorized(team, WRITE)).thenReturn(true);
    when(reportDefinitionRepository.findById(team.getId())).thenReturn(Optional.of(team));
    when(reportGroupRepository.findById(leadsGroup.getId())).thenReturn(Optional.of(leadsGroup));

    reportService.update(team.getId(), "Überstunden Team", "select 2", leadsGroup.getId());

    assertThat(team.getGroup()).isSameAs(leadsGroup);
    verify(reportGroupRepository, never()).save(any());
  }

  @Test
  void whoever_writes_through_a_rule_leaves_the_group_as_it_is() {
    when(reportAuthorization.isAuthorized(eq(umsatz), any())).thenReturn(true);
    when(reportDefinitionRepository.findById(umsatz.getId())).thenReturn(Optional.of(umsatz));

    reportService.update(umsatz.getId(), "Umsatz", "select 2", monatsabschluss.getId());

    assertThat(umsatz.getGroup()).isSameAs(controlling);
  }

  private void actingAsPeopleLead() {
    when(authorizedUser.isManager()).thenReturn(false);
    when(authorizedUser.isPeopleLead()).thenReturn(true);
  }

  private void mayExecute(ReportDefinition... reports) {
    for (var report : reports) {
      when(reportAuthorization.isAuthorized(report, EXECUTE)).thenReturn(true);
    }
  }

  private static ReportGroup group(long id, String name) {
    var group = new ReportGroup();
    ReflectionTestUtils.setField(group, "id", id);
    group.setName(name);
    return group;
  }

  private static ReportGroup personalGroup(long id, long ownerUserId) {
    var owner = new SalatUser();
    ReflectionTestUtils.setField(owner, "id", ownerUserId);
    var group = new ReportGroup();
    ReflectionTestUtils.setField(group, "id", id);
    group.setOwner(owner);
    return group;
  }

  private static ReportDefinition report(long id, String name, ReportGroup group) {
    var report = new ReportDefinition();
    ReflectionTestUtils.setField(report, "id", id);
    report.setName(name);
    report.setSql("select 1");
    report.setGroup(group);
    return report;
  }
}
