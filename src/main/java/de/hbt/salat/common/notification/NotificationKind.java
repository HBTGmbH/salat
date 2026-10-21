package de.hbt.salat.common.notification;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A kind of notification a person can route to the bell, to e-mail or to both (#828). It belongs to
 * the module that notifies: that module declares it as a constant and contributes it through a
 * {@link NotificationKindContributor} — the pattern of {@code BetaFeature}. The notification module
 * that stores and sends knows none of them.
 *
 * <p>The bell is on by default for every kind. Whether e-mail is on by default is the kind's
 * {@link #isEmailByDefault() default}: every mail Salat sent before #828 stays on, a new one starts
 * off. A kind whose mail is {@link #isEmailMandatory() mandatory} is mailed whatever the person chose:
 * a reminder somebody sends on purpose by pressing a button must arrive, and its sender is told when
 * it does not.
 *
 * <p>A kind that only reaches people leads — it is about the bookings of their team — is
 * {@link #forPeopleLeads() marked} as such; the settings page shows it to everybody else greyed out.
 *
 * <p>The person's choices are stored by {@link #getKey()}; a choice for a kind that has since been
 * removed is ignored when read.
 */
public final class NotificationKind {

  /** Lower case letters, digits and dashes; the key goes into message keys and form values. */
  private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

  private final String key;
  private final int position;
  private final boolean emailByDefault;
  private final boolean emailMandatory;
  private final boolean peopleLeadsOnly;

  private NotificationKind(String key, int position, boolean emailByDefault, boolean emailMandatory,
                           boolean peopleLeadsOnly) {
    if (key == null || !KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("notification kind must be lower case letters, digits and dashes: " + key);
    }
    this.key = key;
    this.position = position;
    this.emailByDefault = emailByDefault;
    this.emailMandatory = emailMandatory;
    this.peopleLeadsOnly = peopleLeadsOnly;
  }

  /**
   * A kind the person switches freely.
   *
   * @param position       where the kind stands among the others on the settings page, ascending
   * @param emailByDefault whether it is mailed until the person decides otherwise
   */
  public static NotificationKind of(String key, int position, boolean emailByDefault) {
    return new NotificationKind(key, position, emailByDefault, false, false);
  }

  /** A kind that is always mailed; only its bell can be switched off. */
  public static NotificationKind mailedAlways(String key, int position) {
    return new NotificationKind(key, position, true, true, false);
  }

  /** The same kind, reaching people leads only. */
  public NotificationKind forPeopleLeads() {
    return new NotificationKind(key, position, emailByDefault, emailMandatory, true);
  }

  public String getKey() {
    return key;
  }

  public int getPosition() {
    return position;
  }

  public boolean isEmailByDefault() {
    return emailByDefault;
  }

  public boolean isEmailMandatory() {
    return emailMandatory;
  }

  public boolean isPeopleLeadsOnly() {
    return peopleLeadsOnly;
  }

  /** Message key of the row on the settings page. */
  public String labelKey() {
    return "main.settings.notification.kind." + key + ".label";
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof NotificationKind kind && kind.key.equals(key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key);
  }

  @Override
  public String toString() {
    return key;
  }
}
