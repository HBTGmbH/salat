package org.tb.common.viewhelper;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

/**
 * Presentation helper for building Gravatar image URLs for use in views/templates.
 */
@Component
public class GravatarViewHelper {

  private static final int DEFAULT_SIZE = 128; // matches avatar-xl roughly
  private static final String DEFAULT_IMAGE = "mp"; // mystery person

  /**
   * Build a Gravatar URL for the given email using sensible defaults.
   *
   * @param email the user's email address (may be null)
   * @return the Gravatar image URL with default size and image fallback
   */
  public String gravatarUrl(String email) {
    return gravatarUrl(email, DEFAULT_SIZE);
  }

  /**
   * Build a Gravatar URL for the given email and desired size.
   *
   * @param email the user's email address (may be null)
   * @param size  the desired image size in pixels (Gravatar supports 1-2048)
   * @return the Gravatar image URL
   */
  public String gravatarUrl(String email, int size) {
    String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    String hash = DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));
    int s = size > 0 ? size : DEFAULT_SIZE;
    return "https://www.gravatar.com/avatar/" + hash + "?s=" + s + "&d=" + DEFAULT_IMAGE;
  }
}
