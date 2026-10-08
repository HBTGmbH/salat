package de.hbt.salat.secret.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.secret.domain.SecretStatus;
import de.hbt.salat.secret.domain.SecretType;
import de.hbt.salat.secret.domain.Token;
import de.hbt.salat.secret.domain.UsernamePassword;
import de.hbt.salat.secret.persistence.SecretRepository;

/**
 * Storing secrets encrypted (#1432, ADR-0038), against the database and with the key of the test
 * configuration.
 */
@SpringBootTest
@Transactional
@DisplayNameGeneration(ReplaceUnderscores.class)
class SecretServiceTest {

  private static final String PASSWORD = "geheimes-api-token";

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @Autowired
  private SecretService secretService;

  @Autowired
  private SecretRepository repository;

  @Autowired
  private EntityManager entityManager;

  private ListAppender<ILoggingEvent> log;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("mgr");
    log = new ListAppender<>();
    log.start();
    ((Logger) LoggerFactory.getLogger(SecretService.class)).addAppender(log);
  }

  @AfterEach
  void tearDown() {
    ((Logger) LoggerFactory.getLogger(SecretService.class)).detachAppender(log);
  }

  @Test
  void a_secret_is_read_back_as_it_was_stored() {
    var id = secretService.create(new UsernamePassword("jira-user", PASSWORD));
    flushAndClear();

    assertThat(secretService.read(id)).isEqualTo(new UsernamePassword("jira-user", PASSWORD));
  }

  /** Everything but type and status is encrypted, the user name included (ADR-0038 §2). */
  @Test
  void the_database_holds_cipher_text_only() {
    var id = secretService.create(new UsernamePassword("jira-user", PASSWORD));
    flushAndClear();

    var stored = repository.findById(id).orElseThrow();
    var payload = new String(stored.getPayload(), StandardCharsets.ISO_8859_1);
    assertThat(payload).doesNotContain(PASSWORD).doesNotContain("jira-user");
    assertThat(stored.getType()).isEqualTo(SecretType.USERNAME_PASSWORD);
    assertThat(stored.getStatus()).isEqualTo(SecretStatus.VALID);
    assertThat(stored.getKeyId()).isEqualTo("test");
    assertThat(stored.getCreatedby()).isEqualTo("mgr");
  }

  /** The acceptance criterion of #1432: a cipher text copied into another row does not decrypt. */
  @Test
  void a_cipher_text_copied_into_another_row_cannot_be_read() {
    var source = secretService.create(new UsernamePassword("jira-user", PASSWORD));
    var target = secretService.create(new UsernamePassword("other-user", "other-token"));
    flushAndClear();

    repository.findById(target).orElseThrow().setPayload(repository.findById(source).orElseThrow().getPayload());
    flushAndClear();

    assertThatThrownBy(() -> secretService.read(target))
        .isInstanceOf(BusinessRuleException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.SE_SECRET_UNREADABLE);
  }

  /** A secret from another environment: a message, not an error page, and the form asks again. */
  @Test
  void a_secret_with_an_unknown_key_is_reported_as_unreadable() {
    var id = secretService.create(new UsernamePassword("jira-user", PASSWORD));
    flushAndClear();
    repository.findById(id).orElseThrow().setKeyId("prod");
    flushAndClear();

    assertThatThrownBy(() -> secretService.read(id))
        .isInstanceOf(BusinessRuleException.class)
        .extracting(ex -> firstCode((ErrorCodeException) ex))
        .isEqualTo(ErrorCode.SE_SECRET_UNREADABLE);
    assertThat(secretService.getSummary(id).readable()).isFalse();
  }

  @Test
  void the_summary_names_the_user_but_not_the_password() {
    var id = secretService.create(new UsernamePassword("jira-user", PASSWORD));

    var summary = secretService.getSummary(id);

    assertThat(summary.readable()).isTrue();
    assertThat(summary.username()).isEqualTo("jira-user");
    assertThat(summary.type()).isEqualTo(SecretType.USERNAME_PASSWORD);
    assertThat(summary.toString()).doesNotContain(PASSWORD);
  }

  @Test
  void replacing_may_change_the_type() {
    var id = secretService.create(new UsernamePassword("jira-user", PASSWORD));

    secretService.replace(id, new Token("pat"));
    flushAndClear();

    assertThat(secretService.read(id)).isEqualTo(new Token("pat"));
    assertThat(secretService.getSummary(id).username()).isNull();
  }

  /** Deleted for good: a secret that is only marked as deleted is still stored (ADR-0038 §2). */
  @Test
  void a_deleted_secret_is_gone() {
    var id = secretService.create(new Token("pat"));

    secretService.delete(id);
    flushAndClear();

    assertThat(repository.findById(id)).isEmpty();
    assertThatThrownBy(() -> secretService.read(id)).isInstanceOf(InvalidDataException.class);
  }

  @Test
  void no_secret_reaches_the_log_or_a_to_string() {
    var id = secretService.create(new UsernamePassword("jira-user", PASSWORD));
    secretService.replace(id, new Token(PASSWORD));
    secretService.delete(id);

    assertThat(log.list).extracting(ILoggingEvent::getFormattedMessage)
        .containsExactly(
            "Secret " + id + " (USERNAME_PASSWORD) created by mgr",
            "Secret " + id + " (TOKEN) replaced by mgr",
            "Secret " + id + " (TOKEN) deleted by mgr");
    assertThat(new UsernamePassword("jira-user", PASSWORD).toString()).doesNotContain(PASSWORD);
    assertThat(new Token(PASSWORD).toString()).doesNotContain(PASSWORD);
  }

  @Test
  void only_the_management_reaches_secrets() {
    var id = secretService.create(new Token("pat"));
    when(authorizedUser.isManager()).thenReturn(false);

    assertThatThrownBy(() -> secretService.read(id)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> secretService.create(new Token("x"))).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> secretService.getSummary(id)).isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> secretService.delete(id)).isInstanceOf(AuthorizationException.class);
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private static ErrorCode firstCode(ErrorCodeException ex) {
    return ex.getMessages().get(0).getErrorCode();
  }
}
