package de.hbt.salat.employee.preferences;

import de.hbt.salat.common.notification.NotificationKind;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Through which channels a person wants each kind of notification (#828), stored as the deviations
 * from the defaults: the kinds whose bell is off, and the kinds mailed against their default. A kind
 * nobody touched is therefore stored nowhere, and a new kind starts with its defaults for everybody.
 * The key of a kind that has since been removed is ignored, and gone with the next save.
 */
public record NotificationChannelPreferences(Set<String> bellOff, Set<String> mailOn, Set<String> mailOff) {

  public static final String MODULE_KEY = "notification";
  static final String KEY_BELL_OFF = "bellOff";
  static final String KEY_MAIL_ON = "mailOn";
  static final String KEY_MAIL_OFF = "mailOff";

  public NotificationChannelPreferences {
    bellOff = Set.copyOf(bellOff);
    mailOn = Set.copyOf(mailOn);
    mailOff = Set.copyOf(mailOff);
  }

  public static NotificationChannelPreferences defaults() {
    return new NotificationChannelPreferences(Set.of(), Set.of(), Set.of());
  }

  public static NotificationChannelPreferences from(Map<String, Object> module) {
    if (module == null) return defaults();
    return new NotificationChannelPreferences(keys(module.get(KEY_BELL_OFF)), keys(module.get(KEY_MAIL_ON)),
        keys(module.get(KEY_MAIL_OFF)));
  }

  /**
   * The choices the settings page sends: the kinds whose bell and whose mail are switched on. A kind
   * that is always mailed keeps its mail, whatever is sent for it.
   */
  public static NotificationChannelPreferences of(Collection<NotificationKind> kinds, Collection<String> bellOn,
                                                  Collection<String> mailOn) {
    var bell = bellOn != null ? Set.copyOf(bellOn) : Set.<String>of();
    var mail = mailOn != null ? Set.copyOf(mailOn) : Set.<String>of();
    var bellOff = new TreeSet<String>();
    var mailOnAgainstDefault = new TreeSet<String>();
    var mailOffAgainstDefault = new TreeSet<String>();
    for (var kind : kinds) {
      if (!bell.contains(kind.getKey())) bellOff.add(kind.getKey());
      if (kind.isEmailMandatory()) continue;
      boolean mailed = mail.contains(kind.getKey());
      if (mailed && !kind.isEmailByDefault()) mailOnAgainstDefault.add(kind.getKey());
      if (!mailed && kind.isEmailByDefault()) mailOffAgainstDefault.add(kind.getKey());
    }
    return new NotificationChannelPreferences(bellOff, mailOnAgainstDefault, mailOffAgainstDefault);
  }

  public boolean bell(NotificationKind kind) {
    return !bellOff.contains(kind.getKey());
  }

  public boolean mail(NotificationKind kind) {
    if (kind.isEmailMandatory()) return true;
    return kind.isEmailByDefault() ? !mailOff.contains(kind.getKey()) : mailOn.contains(kind.getKey());
  }

  /** Only what deviates is stored, so that the defaults leave no section behind. */
  public Map<String, Object> toMap() {
    var map = new HashMap<String, Object>();
    if (!bellOff.isEmpty()) map.put(KEY_BELL_OFF, List.copyOf(new TreeSet<>(bellOff)));
    if (!mailOn.isEmpty()) map.put(KEY_MAIL_ON, List.copyOf(new TreeSet<>(mailOn)));
    if (!mailOff.isEmpty()) map.put(KEY_MAIL_OFF, List.copyOf(new TreeSet<>(mailOff)));
    return map;
  }

  private static Set<String> keys(Object stored) {
    if (!(stored instanceof Collection<?> collection)) return Set.of();
    var keys = new TreeSet<String>();
    collection.stream().filter(String.class::isInstance).map(String.class::cast).forEach(keys::add);
    return keys;
  }
}
