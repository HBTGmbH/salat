package org.tb.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.test.FixedClock;
import org.tb.common.util.DateUtils;
import org.tb.jira.domain.JiraApiFlavor;
import org.tb.jira.domain.JiraFieldOption;
import org.tb.jira.domain.JiraReplicationConfig;
import org.tb.jira.domain.JiraReplicationConfigData;
import org.tb.jira.persistence.JiraReplicationConfigRepository;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Maintaining the replication configs from the user interface (#984).
 */
@FixedClock
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JiraReplicationConfigServiceTest {

  private static final long ID = 42L;
  private static final String STORED_PASSWORD = "stored-token";

  @InjectMocks
  private JiraReplicationConfigService classUnderTest;

  @Mock
  private JiraReplicationConfigRepository configRepository;

  @Mock
  private JiraReplicationService jiraReplicationService;

  @Mock
  private JiraSearchClients jiraSearchClients;

  @Mock
  private JiraSearchClient jiraSearchClient;

  @Mock
  private CustomerorderService customerorderService;

  @Mock
  private SuborderService suborderService;

  @Mock
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isManager()).thenReturn(true);
    when(customerorderService.getCustomerorderBySign(any())).thenReturn(new Customerorder());
    when(suborderService.existsSuborderWithCompleteOrderSign(any())).thenReturn(true);
    when(configRepository.save(any())).thenAnswer(invocation -> {
      JiraReplicationConfig config = invocation.getArgument(0);
      if (config.getId() == null) {
        setId(config, ID);
      }
      return config;
    });
  }

  @Test
  void an_empty_password_keeps_the_stored_one() {
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, data(null));

    assertThat(saved().getPassword()).isEqualTo(STORED_PASSWORD);
  }

  @Test
  void a_filled_password_replaces_the_stored_one() {
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));

    classUnderTest.update(ID, data("  new-token  "));

    assertThat(saved().getPassword()).isEqualTo("new-token");
  }

  @Test
  void a_new_replication_needs_a_password() {
    // On an edit an empty field means "keep"; there is nothing to keep on a new record, and the
    // replication would fail on its first run.
    assertThatThrownBy(() -> classUnderTest.create(data("   ")))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_PASSWORD_REQUIRED);

    verify(configRepository, never()).save(any());
  }

  @Test
  void what_the_user_interface_gets_to_see_carries_no_password() {
    var stored = existingConfig();
    when(configRepository.findAllByOrderByNameAsc()).thenReturn(List.of(stored));
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    // The absence is structural: the record has no password component at all, so neither a template
    // nor a log line can reach the stored token.
    assertThat(classUnderTest.getAll()).singleElement()
        .satisfies(info -> assertThat(info.username()).isEqualTo("jira-user"));
    assertThat(classUnderTest.getById(ID).getClass().getRecordComponents())
        .noneMatch(component -> component.getName().toLowerCase().contains("password"));
  }

  @Test
  void the_watermark_is_reset_rather_than_written() {
    var stored = existingConfig();
    stored.setLastMaxUpdated(LocalDateTime.of(2026, 3, 1, 2, 0));
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.resetWatermark(ID);

    assertThat(saved().getLastMaxUpdated()).isNull();
  }

  @Test
  void an_edit_leaves_the_watermark_alone() {
    var stored = existingConfig();
    var watermark = LocalDateTime.of(2026, 3, 1, 2, 0);
    stored.setLastMaxUpdated(watermark);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, data("new-token"));

    assertThat(saved().getLastMaxUpdated()).isEqualTo(watermark);
  }

  @Test
  void switching_a_replication_off_and_on_writes_only_that_flag() {
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.setEnabled(ID, false);
    assertThat(saved().getEnabled()).isFalse();

    classUnderTest.setEnabled(ID, true);
    assertThat(saved().getEnabled()).isTrue();
    assertThat(saved().getPassword()).isEqualTo(STORED_PASSWORD);
  }

  @Test
  void a_failed_run_is_reported_back_without_the_password_in_it() {
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));
    doThrow(new IllegalStateException("401 for user jira-user with token " + STORED_PASSWORD))
        .when(jiraReplicationService).runReplication(stored);

    var outcome = classUnderTest.runNow(ID);

    assertThat(outcome.success()).isFalse();
    assertThat(outcome.name()).isEqualTo("Alpha");
    assertThat(outcome.message()).doesNotContain(STORED_PASSWORD).contains("***");
  }

  @Test
  void a_run_started_by_hand_does_not_ask_whether_the_replication_is_enabled() {
    // Trying a config out before switching it on is the point of the button.
    var stored = existingConfig();
    stored.setEnabled(false);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    assertThat(classUnderTest.runNow(ID).success()).isTrue();

    verify(jiraReplicationService).runReplication(stored);
  }

  @Test
  void a_replication_without_a_jql_query_could_only_ever_fail() {
    var withoutJql = new JiraReplicationConfigData("Alpha", "ALPHA", "https://jira.example.com",
        JiraApiFlavor.SERVER, "jira-user", "token", "  ", null, null, null, null, true, false, null);

    assertThatThrownBy(() -> classUnderTest.create(withoutJql))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_JQL_REQUIRED);
  }

  @Test
  void a_base_url_without_a_scheme_is_rejected() {
    var badUrl = new JiraReplicationConfigData("Alpha", "ALPHA", "jira.example.com",
        JiraApiFlavor.SERVER, "jira-user", "token", "project = ALPHA", null, null, null, null, true, false, null);

    assertThatThrownBy(() -> classUnderTest.create(badUrl))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_BASE_URL_INVALID);
  }

  @Test
  void a_page_size_of_zero_or_less_is_rejected() {
    var zeroPageSize = new JiraReplicationConfigData("Alpha", "ALPHA", "https://jira.example.com",
        JiraApiFlavor.SERVER, "jira-user", "token", "project = ALPHA", null, null, null, 0, true, false, null);

    assertThatThrownBy(() -> classUnderTest.create(zeroPageSize))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_PAGE_SIZE_INVALID);
  }

  @Test
  void a_missing_flavor_is_stored_as_server() {
    // What a row without an explicit flavor has always meant.
    classUnderTest.create(new JiraReplicationConfigData("Alpha", "ALPHA", "https://jira.example.com",
        null, "jira-user", "token", "project = ALPHA", null, null, null, null, true, false, null));

    assertThat(saved().getApiFlavor()).isEqualTo(JiraApiFlavor.SERVER);
  }

  @Test
  void switching_the_worklog_sync_on_without_a_date_starts_today() {
    // The first run must not carry the whole history of the order into JIRA (#1007).
    classUnderTest.create(withWorklogSync("ALPHA", true, null));

    assertThat(saved().getWorklogSyncFrom()).isEqualTo(DateUtils.today());
  }

  @Test
  void a_start_date_that_was_entered_is_kept() {
    // Moving it back is how a period is filled in afterwards, on purpose.
    var backfill = LocalDate.of(2026, 1, 1);

    classUnderTest.create(withWorklogSync("ALPHA", true, backfill));

    assertThat(saved().getWorklogSyncFrom()).isEqualTo(backfill);
  }

  @Test
  void a_replication_without_the_worklog_sync_gets_no_start_date() {
    classUnderTest.create(withWorklogSync("ALPHA", false, null));

    assertThat(saved().getWorklogSyncFrom()).isNull();
  }

  @Test
  void switching_the_worklog_sync_off_keeps_the_start_date() {
    // What SALAT wrote stays in JIRA and stays remembered; switching on again picks up where it
    // left off instead of starting a second period next to the first.
    var stored = existingConfig();
    stored.setWorklogSyncEnabled(true);
    stored.setWorklogSyncFrom(LocalDate.of(2026, 1, 1));
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withWorklogSync("ALPHA", false, LocalDate.of(2026, 1, 1)));

    assertThat(stored.getWorklogSyncFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(stored.getWorklogSyncEnabled()).isFalse();
  }

  @Test
  void a_second_worklog_sync_over_the_same_branch_of_the_same_instance_is_refused() {
    // An order-wide replication and one for a suborder inside it would each write their own worklog
    // on the same ticket and day — the time would stand twice in JIRA, and nothing in SALAT shows
    // it.
    givenSuborderScope("ALPHA/01", "ALPHA");
    givenOtherReplication("ALPHA/01", "https://jira.example.com", true);

    assertThatThrownBy(() -> classUnderTest.create(withWorklogSync("ALPHA", true, null)))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_WORKLOG_SCOPE_OVERLAP);
  }

  @Test
  void the_same_branch_on_another_jira_instance_is_allowed() {
    // Different installations share no issue keys, so there is nothing to collide.
    givenSuborderScope("ALPHA/01", "ALPHA");
    givenOtherReplication("ALPHA/01", "https://other-jira.example.com", true);

    classUnderTest.create(withWorklogSync("ALPHA", true, null));

    assertThat(saved().getWorklogSyncEnabled()).isTrue();
  }

  @Test
  void an_overlapping_replication_that_writes_no_worklogs_is_no_obstacle() {
    givenSuborderScope("ALPHA/01", "ALPHA");
    givenOtherReplication("ALPHA/01", "https://jira.example.com", false);

    classUnderTest.create(withWorklogSync("ALPHA", true, null));

    assertThat(saved().getWorklogSyncEnabled()).isTrue();
  }

  @Test
  void a_replication_does_not_collide_with_itself_when_it_is_edited() {
    var stored = existingConfig();
    setId(stored, ID);
    stored.setWorklogSyncEnabled(true);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));
    when(configRepository.findAllByOrderByNameAsc()).thenReturn(List.of(stored));

    classUnderTest.update(ID, withWorklogSync("ALPHA", true, null));

    assertThat(stored.getWorklogSyncEnabled()).isTrue();
  }

  @Test
  void two_orders_whose_signs_read_like_a_path_are_not_an_overlap() {
    // 0283 and 0283/03.20 are two customer orders, not an order and its suborder — comparing the
    // signs as strings would call them an overlap.
    when(customerorderService.getCustomerorderBySign("0283")).thenReturn(new Customerorder());
    when(customerorderService.getCustomerorderBySign("0283/03.20")).thenReturn(new Customerorder());
    givenOtherReplication("0283/03.20", "https://jira.example.com", true);

    classUnderTest.create(withWorklogSync("0283", true, null));

    assertThat(saved().getWorklogSyncEnabled()).isTrue();
  }

  @Test
  void deleting_a_replication_leaves_the_replicated_tickets_alone() {
    // jira_ticket hangs off scope_sign, not off the config — the rows are not wrong, only no
    // longer kept up to date.
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.delete(ID);

    verify(configRepository).delete(stored);
    verifyNoInteractions(jiraReplicationService);
  }

  @Test
  void an_unknown_replication_is_reported_as_such() {
    when(configRepository.findById(ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> classUnderTest.getById(ID))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_NOT_FOUND);
  }

  @Test
  void everything_here_needs_the_management() {
    when(authorizedUser.isManager()).thenReturn(false);

    assertThatThrownBy(() -> classUnderTest.getAll()).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.getById(ID)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.create(data("token"))).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.update(ID, data("token"))).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.delete(ID)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.setEnabled(ID, true)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.resetWatermark(ID)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.runNow(ID)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.getSelectableFields(ID)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.customerorderSignOf("ALPHA")).isInstanceOf(AuthorizationException.class);

    verifyNoInteractions(configRepository, jiraReplicationService, jiraSearchClients,
        customerorderService, suborderService);
  }

  @Test
  void changing_the_field_list_resets_the_watermark() {
    // Otherwise the search keeps every already replicated ticket out and the newly configured
    // fields reach nothing but the tickets edited in JIRA afterwards (#881).
    var stored = existingConfig();
    stored.setLastMaxUpdated(LocalDateTime.of(2026, 6, 1, 8, 0));
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withFields("customfield_10123", "customfield_10123"));

    assertThat(saved().getLastMaxUpdated()).isNull();
    assertThat(saved().getAdditionalFieldNames()).isEqualTo("customfield_10123");
    assertThat(saved().getInheritedFieldNames()).isEqualTo("customfield_10123");
  }

  @Test
  void an_edit_that_leaves_the_field_list_alone_keeps_the_watermark() {
    var watermark = LocalDateTime.of(2026, 6, 1, 8, 0);
    var stored = existingConfig();
    stored.setLastMaxUpdated(watermark);
    stored.setAdditionalFieldNames("customfield_10123");
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withFields(" customfield_10123 ", null));

    assertThat(saved().getLastMaxUpdated()).isEqualTo(watermark);
  }

  @Test
  void a_replication_can_be_scoped_to_one_suborder_of_any_depth() {
    // the scope is stored as the fully qualified sign — the suborder sign alone is not unique
    when(customerorderService.getCustomerorderBySign("ALPHA/A/01")).thenReturn(null);

    classUnderTest.create(withScope("ALPHA/A/01", "token"));

    assertThat(saved().getScopeSign()).isEqualTo("ALPHA/A/01");
    verify(suborderService).existsSuborderWithCompleteOrderSign("ALPHA/A/01");
  }

  @Test
  void an_order_sign_that_carries_a_slash_is_still_an_order_wide_scope() {
    // "0283/03.20" is one order. Deciding by the shape of the sign - slash means suborder - would
    // refuse the order-wide replication that works today, and eleven orders here carry one.
    when(customerorderService.getCustomerorderBySign("0283/03.20")).thenReturn(new Customerorder());

    classUnderTest.create(withScope("0283/03.20", "token"));

    assertThat(saved().getScopeSign()).isEqualTo("0283/03.20");
    verifyNoInteractions(suborderService);
  }

  @Test
  void a_scope_that_is_neither_an_order_nor_a_suborder_is_refused() {
    // otherwise a replication is created that nothing ever reads: the suggestions resolve the
    // branch of the booked suborder, and a sign outside every branch never matches
    when(customerorderService.getCustomerorderBySign("ALPHA/nope")).thenReturn(null);
    when(suborderService.existsSuborderWithCompleteOrderSign("ALPHA/nope")).thenReturn(false);

    assertThatThrownBy(() -> classUnderTest.create(withScope("ALPHA/nope", "token")))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_SCOPE_NOT_FOUND);

    verify(configRepository, never()).save(any());
  }

  @Test
  void an_unknown_customer_order_is_refused_just_the_same() {
    when(customerorderService.getCustomerorderBySign("NOPE")).thenReturn(null);
    when(suborderService.existsSuborderWithCompleteOrderSign("NOPE")).thenReturn(false);

    assertThatThrownBy(() -> classUnderTest.create(withScope("NOPE", "token")))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_SCOPE_NOT_FOUND);
  }

  @Test
  void the_order_behind_an_order_wide_scope_is_the_scope_itself() {
    assertThat(classUnderTest.customerorderSignOf("ALPHA")).isEqualTo("ALPHA");

    verifyNoInteractions(suborderService);
  }

  @Test
  void the_order_behind_a_suborder_scope_is_asked_for_rather_than_parsed() {
    // the edit form loads the suborders of this order, and an order sign may carry a slash itself:
    // splitting "0283/03.20/F&E/01" at its first slash would name the unrelated order "0283",
    // open the form there with nothing preselected, and move the replication on the next save
    when(customerorderService.getCustomerorderBySign("0283/03.20/F&E/01")).thenReturn(null);
    when(suborderService.getSuborderByCompleteOrderSign("0283/03.20/F&E/01"))
        .thenReturn(suborderOf("0283/03.20"));

    assertThat(classUnderTest.customerorderSignOf("0283/03.20/F&E/01")).isEqualTo("0283/03.20");
  }

  @Test
  void a_scope_whose_order_is_gone_is_left_as_it_is_so_it_can_be_corrected() {
    when(customerorderService.getCustomerorderBySign("GONE/01")).thenReturn(null);
    when(suborderService.getSuborderByCompleteOrderSign("GONE/01")).thenReturn(null);

    assertThat(classUnderTest.customerorderSignOf("GONE/01")).isEqualTo("GONE/01");
  }

  @Test
  void a_replication_without_a_scope_is_refused_before_anything_is_looked_up() {
    assertThatThrownBy(() -> classUnderTest.create(withScope("  ", "token")))
        .isInstanceOf(InvalidDataException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.JI_REPLICATION_SCOPE_REQUIRED);
  }

  @Test
  void moving_a_replication_to_another_scope_resets_the_watermark() {
    // the new scope has no tickets of its own yet, and with the watermark in place the search would
    // only ever find what JIRA has touched since (#1025)
    var stored = existingConfig();
    stored.setLastMaxUpdated(LocalDateTime.of(2026, 6, 1, 8, 0));
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withScope("ALPHA/A/01", null));

    assertThat(saved().getScopeSign()).isEqualTo("ALPHA/A/01");
    assertThat(saved().getLastMaxUpdated()).isNull();
  }

  @Test
  void an_edit_that_leaves_the_scope_alone_keeps_the_watermark() {
    var watermark = LocalDateTime.of(2026, 6, 1, 8, 0);
    var stored = existingConfig();
    stored.setLastMaxUpdated(watermark);
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));

    classUnderTest.update(ID, withScope("  ALPHA  ", null));

    assertThat(saved().getLastMaxUpdated()).isEqualTo(watermark);
  }

  @Test
  void the_field_catalogue_is_fetched_with_the_stored_credentials() {
    // never with values from the form - otherwise a manager could point the server at any address
    // it can reach and get the authenticated answer shown back
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));
    when(jiraSearchClients.forFlavor(JiraApiFlavor.SERVER)).thenReturn(jiraSearchClient);
    when(jiraSearchClient.listFields(any())).thenReturn(List.of());

    classUnderTest.getSelectableFields(ID);

    var request = ArgumentCaptor.forClass(JiraFieldsRequest.class);
    verify(jiraSearchClient).listFields(request.capture());
    assertThat(request.getValue().baseUrl()).isEqualTo("https://jira.example.com");
    assertThat(request.getValue().username()).isEqualTo("jira-user");
    assertThat(request.getValue().password()).isEqualTo(STORED_PASSWORD);
  }

  @Test
  void a_cascading_select_is_offered_with_its_second_level() {
    givenCatalogue(
        field("customfield_10200", "Kategorie", "com.atlassian…customfieldtypes:cascadingselect", null),
        field("customfield_10123", "Abrechnung", "com.atlassian…customfieldtypes:select", null),
        field("duedate", "Fälligkeitsdatum", null, "date"));

    var catalogue = classUnderTest.getSelectableFields(ID);

    // sorted by name, and the second level sits right behind the field it belongs to
    assertThat(catalogue.options()).extracting(JiraFieldOption::key).containsExactly(
        "customfield_10123", "duedate", "customfield_10200", "customfield_10200.child.value");
    assertThat(catalogue.options()).extracting(JiraFieldOption::type).containsExactly(
        "select", "date", "cascadingselect", "cascadingselect");
    assertThat(catalogue.options().get(3).secondLevel()).isTrue();
    assertThat(catalogue.hasError()).isFalse();
  }

  @Test
  void an_umlaut_sorts_where_a_reader_looks_for_it() {
    // comparing code points would file this behind "Zeiterfassung", which reads as broken in a list
    // somebody scans by name
    givenCatalogue(
        field("customfield_10300", "Zeiterfassung", null, "string"),
        field("customfield_10301", "Änderungsdatum", null, "date"));

    assertThat(classUnderTest.getSelectableFields(ID).options())
        .extracting(JiraFieldOption::name)
        .containsExactly("Änderungsdatum", "Zeiterfassung");
  }

  @Test
  void a_field_without_a_name_falls_back_to_its_key() {
    givenCatalogue(field("customfield_10400", null, null, "string"));

    assertThat(classUnderTest.getSelectableFields(ID).options())
        .extracting(JiraFieldOption::name).containsExactly("customfield_10400");
  }

  @Test
  void a_failed_catalogue_is_reported_back_without_the_password_in_it() {
    var stored = existingConfig();
    when(configRepository.findById(ID)).thenReturn(Optional.of(stored));
    when(jiraSearchClients.forFlavor(JiraApiFlavor.SERVER)).thenReturn(jiraSearchClient);
    when(jiraSearchClient.listFields(any()))
        .thenThrow(new IllegalStateException("401 for user:" + STORED_PASSWORD));

    var catalogue = classUnderTest.getSelectableFields(ID);

    assertThat(catalogue.hasError()).isTrue();
    assertThat(catalogue.errorMessage()).doesNotContain(STORED_PASSWORD).contains("***");
    assertThat(catalogue.options()).isEmpty();
  }

  private void givenCatalogue(JiraField... fields) {
    when(configRepository.findById(ID)).thenReturn(Optional.of(existingConfig()));
    when(jiraSearchClients.forFlavor(JiraApiFlavor.SERVER)).thenReturn(jiraSearchClient);
    when(jiraSearchClient.listFields(any())).thenReturn(List.of(fields));
  }

  private static JiraField field(String id, String name, String customType, String type) {
    var field = new JiraField();
    field.setId(id);
    field.setName(name);
    var schema = new JiraField.Schema();
    schema.setCustom(customType);
    schema.setType(type);
    field.setSchema(schema);
    return field;
  }

  private static JiraReplicationConfigData withFields(String additional, String inherited) {
    return new JiraReplicationConfigData("Alpha", "ALPHA", "https://jira.example.com",
        JiraApiFlavor.SERVER, "jira-user", null, "project = ALPHA", null, additional, inherited,
        100, true, false, null);
  }

  private JiraReplicationConfig existingConfig() {
    var config = new JiraReplicationConfig();
    config.setName("Alpha");
    config.setScopeSign("ALPHA");
    config.setBaseUrl("https://jira.example.com");
    config.setApiFlavor(JiraApiFlavor.SERVER);
    config.setUsername("jira-user");
    config.setPassword(STORED_PASSWORD);
    config.setJql("project = ALPHA");
    config.setEnabled(true);
    return config;
  }

  private static Suborder suborderOf(String customerorderSign) {
    var customerorder = new Customerorder();
    customerorder.setSign(customerorderSign);
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    return suborder;
  }

  private static JiraReplicationConfigData withScope(String scopeSign, String password) {
    return new JiraReplicationConfigData("Alpha", scopeSign, "https://jira.example.com",
        JiraApiFlavor.SERVER, "jira-user", password, "project = ALPHA", null, null, null, 100, true, false, null);
  }

  private static JiraReplicationConfigData withWorklogSync(String scopeSign, boolean enabled,
                                                           LocalDate from) {
    return new JiraReplicationConfigData("Alpha", scopeSign, "https://jira.example.com",
        JiraApiFlavor.SERVER, "jira-user", "token", "project = ALPHA", null, null, null, 100, true,
        enabled, from);
  }

  /** A scope that is a suborder path under the given customer order, not an order of its own. */
  private void givenSuborderScope(String completeOrderSign, String customerorderSign) {
    when(customerorderService.getCustomerorderBySign(completeOrderSign)).thenReturn(null);
    when(suborderService.getSuborderByCompleteOrderSign(completeOrderSign))
        .thenReturn(suborderOf(customerorderSign));
  }

  private void givenOtherReplication(String scopeSign, String baseUrl, boolean worklogSync) {
    var other = new JiraReplicationConfig();
    setId(other, 99L);
    other.setName("Beta");
    other.setScopeSign(scopeSign);
    other.setBaseUrl(baseUrl);
    other.setWorklogSyncEnabled(worklogSync);
    when(configRepository.findAllByOrderByNameAsc()).thenReturn(List.of(other));
  }

  private static JiraReplicationConfigData data(String password) {
    return new JiraReplicationConfigData("Alpha", "ALPHA", "https://jira.example.com",
        JiraApiFlavor.SERVER, "jira-user", password, "project = ALPHA", null, null, null, 100, true, false, null);
  }

  private JiraReplicationConfig saved() {
    var captor = ArgumentCaptor.forClass(JiraReplicationConfig.class);
    verify(configRepository, atLeastOnce()).save(captor.capture());
    return captor.getValue();
  }

  private static ErrorCode firstCode(ErrorCodeException ex) {
    return ex.getMessages().get(0).getErrorCode();
  }

  /** The id is generated, so there is no setter; a stored record always has one. */
  private static void setId(JiraReplicationConfig config, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(config, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
  }
}
