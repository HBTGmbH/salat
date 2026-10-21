package de.hbt.salat.notification.service;

import static de.hbt.salat.testutils.ReferenceTestUtils.salatUserWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.notification.NotificationKind;
import de.hbt.salat.common.notification.NotificationRecipient;
import de.hbt.salat.common.notification.NotificationRecipients;
import de.hbt.salat.common.service.MailService.MailContact;
import de.hbt.salat.notification.domain.Notification;
import de.hbt.salat.notification.domain.NotificationMessage;
import de.hbt.salat.notification.persistence.NotificationRepository;
import de.hbt.salat.notification.persistence.RecipientReferences;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;

/**
 * One way for every notification (#828): to the bell where the recipient wants the kind there, by
 * mail where the recipient wants it mailed or the kind is always mailed.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class NotificationServiceTest {

  private static final NotificationKind SHARED = NotificationKind.of("shared", 10, false);
  private static final NotificationKind REMINDER = NotificationKind.mailedAlways("reminder", 20);
  private static final long LOGIN = 11L;
  private static final NotificationMessage MESSAGE = NotificationMessage.builder()
      .titleKey("title").titleParams(List.of("A"))
      .descriptionKey("description").descriptionParams(List.of("B", "C"))
      .actionUrl("/target").actionLabel("Open")
      .build();

  private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
  private final RecipientReferences recipientReferences = mock(RecipientReferences.class);
  private final NotificationRecipients notificationRecipients = mock(NotificationRecipients.class);
  private final NotificationMailer notificationMailer = mock(NotificationMailer.class);
  private final NotificationService service = new NotificationService(notificationRepository,
      mock(SalatUserRepository.class), recipientReferences, mock(AuthorizedUser.class), mock(MessageSource.class),
      new SalatProperties(), notificationRecipients, notificationMailer);

  @BeforeEach
  void setUp() {
    when(recipientReferences.salatUser(anyLong())).thenAnswer(call -> salatUserWithId(call.getArgument(0)));
  }

  @Test
  void a_recipient_who_wants_the_bell_only_gets_the_notification_stored_and_no_mail() {
    givenRecipient(SHARED, true, false);

    service.emitNotification(SHARED, List.of(LOGIN), MESSAGE);

    var stored = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).save(stored.capture());
    assertThat(stored.getValue().getRecipientUserId()).isEqualTo(LOGIN);
    assertThat(stored.getValue().getTitleKey()).isEqualTo("title");
    assertThat(stored.getValue().getTitleParams()).isEqualTo("[\"A\"]");
    assertThat(stored.getValue().getDescriptionParams()).isEqualTo("[\"B\",\"C\"]");
    assertThat(stored.getValue().getActionUrl()).isEqualTo("/target");
    assertThat(stored.getValue().getActionLabel()).isEqualTo("Open");
    verifyNoInteractions(notificationMailer);
  }

  @Test
  void a_recipient_who_wants_the_mail_only_gets_it_after_the_commit_and_nothing_in_the_bell() {
    var recipient = givenRecipient(SHARED, false, true);

    service.emitNotification(SHARED, List.of(LOGIN), MESSAGE);

    verify(notificationRepository, never()).save(any());
    verify(notificationMailer).mailAfterCommit(recipient, MESSAGE, List.of());
  }

  @Test
  void a_kind_that_is_always_mailed_goes_out_at_once_whatever_the_recipient_chose() {
    var recipient = givenRecipient(REMINDER, true, false);

    service.emitNotification(REMINDER, List.of(LOGIN), MESSAGE);

    verify(notificationMailer).mailNow(recipient, MESSAGE, List.of());
    verify(notificationMailer, never()).mailAfterCommit(any(), any(), any());
  }

  @Test
  void only_the_mail_never_touches_the_bell() {
    var recipient = givenRecipient(SHARED, true, true);

    service.emitMail(SHARED, List.of(LOGIN), MESSAGE);

    verify(notificationRepository, never()).save(any());
    verify(notificationMailer).mailAfterCommit(recipient, MESSAGE, List.of());
  }

  /** #215: the people leads read along when a person is reminded — a copy of the mail, nothing in their bell. */
  @Test
  void a_copy_goes_to_the_cc_logins_with_an_address_and_into_no_bell_of_theirs() {
    var recipient = givenRecipient(REMINDER, false, false);
    var lead = new NotificationRecipient(21L, "Carla", "carla@example.org", Locale.GERMAN, true, true);
    var withoutAddress = new NotificationRecipient(22L, "Dirk", null, Locale.GERMAN, true, true);
    when(notificationRecipients.resolve(List.of(21L, 22L), REMINDER)).thenReturn(List.of(lead, withoutAddress));

    service.emitNotification(REMINDER, List.of(LOGIN), List.of(21L, 22L), MESSAGE);

    verify(notificationMailer).mailNow(recipient, MESSAGE, List.of(new MailContact("Carla", "carla@example.org")));
    verify(notificationRepository, never()).save(any());
  }

  @Test
  void without_cc_logins_nobody_else_is_asked_for() {
    var recipient = givenRecipient(REMINDER, false, false);

    service.emitNotification(REMINDER, List.of(LOGIN), List.of(), MESSAGE);

    verify(notificationMailer).mailNow(recipient, MESSAGE, List.of());
    verify(notificationRecipients).resolve(List.of(LOGIN), REMINDER);
    verifyNoMoreInteractions(notificationRecipients);
  }

  private NotificationRecipient givenRecipient(NotificationKind kind, boolean bell, boolean mail) {
    var recipient = new NotificationRecipient(LOGIN, "Anna", "anna@example.org", Locale.GERMAN, bell, mail);
    when(notificationRecipients.resolve(List.of(LOGIN), kind)).thenReturn(List.of(recipient));
    return recipient;
  }
}
