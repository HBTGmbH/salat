package de.hbt.salat.common.notification;

import java.util.Locale;

/**
 * A person a notification of one kind goes to, as values (#828): whom to greet and where to write,
 * in which language, and through which channels the person wants this kind.
 *
 * @param salatUserId the login the notification is for
 * @param firstname   the name the mail greets; the login name where no person belongs to it
 * @param email       the address for notifications; {@code null} where there is none
 * @param locale      the language the person chose, German where the browser decides
 * @param bell        whether the kind goes to the bell
 * @param mail        whether the kind is mailed — the person's choice, or its default
 */
public record NotificationRecipient(long salatUserId, String firstname, String email, Locale locale, boolean bell,
                                    boolean mail) {
}
