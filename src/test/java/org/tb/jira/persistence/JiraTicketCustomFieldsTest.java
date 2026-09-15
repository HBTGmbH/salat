package org.tb.jira.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.jira.domain.JiraTicket;
import org.tb.jira.domain.ResolvedFieldValue;

/**
 * The two JSON columns of a ticket (#881) survive a round trip through the database.
 *
 * <p>{@code @JdbcTypeCode(SqlTypes.JSON)} is used nowhere else in the project, so the mapping is
 * worth its own test: it is the one place where a wrong format mapper or a generic type Hibernate
 * cannot reconstruct shows up as data that goes in and does not come back.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraTicketCustomFieldsTest {

  @Autowired
  private JiraTicketRepository jiraTicketRepository;

  @PersistenceContext
  private EntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
  }

  @Test
  void the_raw_values_survive_a_round_trip() {
    var id = save(ticket -> ticket.setCustomFields(Map.of(
        "customfield_10123", "Wartung",
        "customfield_10124", "A,B")));

    assertThat(reload(id).getCustomFields())
        .containsEntry("customfield_10123", "Wartung")
        .containsEntry("customfield_10124", "A,B")
        .hasSize(2);
  }

  @Test
  void the_resolved_values_survive_a_round_trip_with_their_origin() {
    var id = save(ticket -> ticket.setCustomFieldsEffective(Map.of(
        "customfield_10123", new ResolvedFieldValue("Wartung", "ALPHA-1"),
        "customfield_10124", new ResolvedFieldValue("Migration", null))));

    assertThat(reload(id).getCustomFieldsEffective())
        // from = null is the statement "set on the ticket itself", not a missing value
        .containsEntry("customfield_10123", new ResolvedFieldValue("Wartung", "ALPHA-1"))
        .containsEntry("customfield_10124", new ResolvedFieldValue("Migration", null));
  }

  @Test
  void a_ticket_without_configured_fields_keeps_both_columns_empty() {
    // Not an empty document: a native json column cannot hold an empty string, and JSON_EXTRACT on
    // one aborts the statement around it. NULL answers NULL.
    var reloaded = reload(save(ticket -> {}));

    assertThat(reloaded.getCustomFields()).isNull();
    assertThat(reloaded.getCustomFieldsEffective()).isNull();
  }

  private Long save(Consumer<JiraTicket> fill) {
    var ticket = new JiraTicket();
    ticket.setCustomerorderSign("ALPHA");
    ticket.setJiraId(1L);
    ticket.setKey("ALPHA-1");
    ticket.setFieldConfigHash("0123456789abcdef");
    fill.accept(ticket);
    return jiraTicketRepository.save(ticket).getId();
  }

  /** Written, session cleared, read again — otherwise the assertion sees the object it just set. */
  private JiraTicket reload(Long id) {
    entityManager.flush();
    entityManager.clear();
    return jiraTicketRepository.findById(id).orElseThrow();
  }
}
