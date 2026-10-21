package de.hbt.salat.notification.service;

import static de.hbt.salat.common.exception.ErrorCode.XX_MAIL_NOT_SENT;

import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.mail.MailTemplates;
import de.hbt.salat.common.notification.NotificationRecipient;
import de.hbt.salat.common.service.MailService;
import de.hbt.salat.common.service.MailService.EmailRequest;
import de.hbt.salat.common.service.MailService.MailContact;
import de.hbt.salat.notification.domain.NotificationMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Mails a notification (#828), in the language of its recipient: the title is the subject, the
 * description — or the mail's own, where the notification has one — the body, framed by a greeting,
 * the link to its target and the note that the mail was generated. Since #1505 the same comes as HTML
 * in the common layout ({@code templates/mail/notification.html}), the text part beside it.
 *
 * <p>A mail the person may switch off goes out after the commit and never fails what caused it: a
 * release that is rolled back sends nothing, and a mail server that is down leaves a line in the log.
 * A mandatory mail is the point of the action that sends it — a reminder —, so it goes out at once,
 * and a failure is reported to whoever sent it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class NotificationMailer {

  private static final String SENDER_NAME = "Salat";
  private static final String SETTINGS_URL = "/settings";

  private final MailService mailService;
  private final MessageSource messageSource;
  private final SalatProperties salatProperties;
  private final MailTemplates mailTemplates;

  void mailAfterCommit(NotificationRecipient recipient, NotificationMessage message) {
    if (recipient.email() == null || recipient.email().isBlank()) return;
    var request = request(recipient, message);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          sendQuietly(request);
        }
      });
    } else {
      sendQuietly(request);
    }
  }

  /**
   * @throws BusinessRuleException {@code XX-0005} when the mail cannot be sent, or the recipient has
   *                               no address
   */
  void mailNow(NotificationRecipient recipient, NotificationMessage message) {
    if (recipient.email() == null || recipient.email().isBlank()) {
      throw new BusinessRuleException(XX_MAIL_NOT_SENT, recipient.firstname());
    }
    try {
      mailService.sendEmail(request(recipient, message));
    } catch (RuntimeException e) {
      log.error("Could not send the mandatory notification mail {} to login {}", message.titleKey(),
          recipient.salatUserId(), e);
      throw new BusinessRuleException(XX_MAIL_NOT_SENT, recipient.firstname());
    }
  }

  private void sendQuietly(EmailRequest request) {
    try {
      mailService.sendEmail(request);
    } catch (RuntimeException e) {
      log.warn("Could not send the notification mail '{}' to {}", request.getSubject(), request.getTo().getEmail(), e);
    }
  }

  private EmailRequest request(NotificationRecipient recipient, NotificationMessage message) {
    var locale = recipient.locale();
    var title = text(message.titleKey(), locale, message.titleParams().toArray());
    var description = description(message, locale);
    var actionUrl = message.actionUrl() != null ? absolute(message.actionUrl()) : null;
    var settingsUrl = absolute(SETTINGS_URL);
    var html = new HashMap<String, Object>();
    html.put("firstname", recipient.firstname());
    html.put("title", title);
    html.put("descriptionLines", description != null ? description.lines().toList() : List.of());
    html.put("actionUrl", actionUrl);
    html.put("actionLabel", message.actionLabel() != null ? message.actionLabel() : text("main.mail.button.open", locale));
    html.put("settingsUrl", settingsUrl);
    return EmailRequest.builder()
        .subject(text("main.notification.email.subject", locale, title))
        .message(body(recipient, title, description, actionUrl, settingsUrl, locale))
        .htmlMessage(mailTemplates.render("mail/notification", locale, html))
        .from(message.sender() != null ? message.sender() : new MailContact(SENDER_NAME, salatProperties.getNotifications().getEmailFrom()))
        .to(new MailContact(recipient.firstname(), recipient.email()))
        // the footer of MailService is German only
        .appendAutoGeneratedFooter(false)
        .build();
  }

  /** The mail's own text where the notification has one, else its description; {@code null} without either. */
  private String description(NotificationMessage message, Locale locale) {
    if (message.mailDescriptionKey() != null) {
      return text(message.mailDescriptionKey(), locale, message.mailDescriptionParams().toArray());
    }
    return message.descriptionKey() != null
        ? text(message.descriptionKey(), locale, message.descriptionParams().toArray())
        : null;
  }

  /** The text part: what the HTML part says, for whoever reads mails without HTML. */
  private String body(NotificationRecipient recipient, String title, String description, String actionUrl,
                      String settingsUrl, Locale locale) {
    var body = new StringBuilder(text("main.notification.email.greeting", locale, recipient.firstname())).append("\n\n");
    body.append(description != null ? description : title);
    if (actionUrl != null) {
      body.append("\n\n").append(text("main.notification.email.link", locale, actionUrl));
    }
    return body.append("\n\n__________________________\n\n")
        .append(text("main.mail.footer.automatic", locale)).append("\n")
        .append(text("main.mail.footer.notification", locale)).append("\n").append(settingsUrl)
        .toString();
  }

  private String absolute(String path) {
    return (salatProperties.getUrl() != null ? salatProperties.getUrl() : "") + path;
  }

  private String text(String key, Locale locale, Object... arguments) {
    return messageSource.getMessage(key, arguments, locale);
  }
}
