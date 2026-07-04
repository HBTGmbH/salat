package org.tb.employee.preferences;

import java.util.HashMap;
import java.util.Map;

public record EmployeePreferences(String notificationEmail, String gravatarEmail) {

  public static final String MODULE_KEY = "employee";
  static final String KEY_NOTIFICATION_EMAIL = "notificationEmail";
  static final String KEY_GRAVATAR_EMAIL = "gravatarEmail";

  public static EmployeePreferences defaults() {
    return new EmployeePreferences(null, null);
  }

  public static EmployeePreferences from(Map<String, Object> map) {
    try {
      return new EmployeePreferences(
          (String) map.get(KEY_NOTIFICATION_EMAIL),
          (String) map.get(KEY_GRAVATAR_EMAIL)
      );
    } catch (Exception e) {
      return defaults();
    }
  }

  public Map<String, Object> toMap() {
    var map = new HashMap<String, Object>();
    if (notificationEmail != null && !notificationEmail.isBlank()) {
      map.put(KEY_NOTIFICATION_EMAIL, notificationEmail);
    }
    if (gravatarEmail != null && !gravatarEmail.isBlank()) {
      map.put(KEY_GRAVATAR_EMAIL, gravatarEmail);
    }
    return map;
  }

}
