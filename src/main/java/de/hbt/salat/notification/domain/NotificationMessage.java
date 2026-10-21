package de.hbt.salat.notification.domain;

import de.hbt.salat.common.service.MailService.MailContact;
import java.util.List;
import lombok.Builder;

/**
 * What a notification says, as message keys and their arguments (#828): the bell resolves them when
 * it shows the notification, the mail in the language of its recipient when it is sent.
 *
 * @param titleKey          the title in the bell, and the subject of the mail
 * @param titleParams       the arguments of the title
 * @param descriptionKey    the text below the title and the body of the mail; may be {@code null}
 * @param descriptionParams the arguments of the description
 * @param actionUrl         where the notification leads, relative to the application; may be {@code null}
 * @param actionLabel       the label of the link in the bell, already resolved; may be {@code null}
 * @param sender            whom the mail comes from where a person caused it — a reminder, a release;
 *                          {@code null} sends it from the address configured for notifications
 */
@Builder
public record NotificationMessage(String titleKey, List<String> titleParams, String descriptionKey,
                                  List<String> descriptionParams, String actionUrl, String actionLabel,
                                  MailContact sender) {

  public NotificationMessage {
    titleParams = withoutNulls(titleParams);
    descriptionParams = withoutNulls(descriptionParams);
  }

  private static List<String> withoutNulls(List<String> params) {
    return params == null ? List.of() : params.stream().map(param -> param != null ? param : "").toList();
  }
}
