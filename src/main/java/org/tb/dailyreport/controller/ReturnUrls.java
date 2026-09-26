package org.tb.dailyreport.controller;

/**
 * The one check for a return target that arrives with a request (#1133): the redirect after saving
 * or sharing a booking, the next empty form of "Speichern und neu", and the booking form itself,
 * which renders the target as a hidden field and as the link "Abbrechen". Redirect and form accept
 * exactly the same targets.
 *
 * <p>The target is a request parameter, so any link to the form can carry any value. Until #1133
 * only the redirects were checked; the form put the raw value into the cancel link, where a
 * {@code javascript:} address runs script in the context of the application and an absolute or
 * protocol-relative address leaves it unnoticed. The application sets no Content-Security-Policy
 * that would catch either. Hence an allowlist:
 *
 * <ul>
 *   <li>a root-relative path of this application: it starts with {@code /}, but not with
 *       {@code //}, which a browser reads as another host;</li>
 *   <li>no backslash, which a browser treats like a slash ({@code /\host} is {@code //host});</li>
 *   <li>no control character, since a line break would inject a header into the redirect;</li>
 *   <li>it leads into the daily view. That is the prefix rule the controller applied before, so
 *       every target the application builds itself still passes, including the list view with
 *       {@code fMonth}/{@code fYear} and the {@code pathname + search} the share dialog sends.</li>
 * </ul>
 */
final class ReturnUrls {

  private static final String DAILY_VIEW = "/dailyreport/daily";

  private ReturnUrls() {
  }

  static boolean isSafe(String returnUrl) {
    return returnUrl != null
        && returnUrl.startsWith("/")
        && !returnUrl.startsWith("//")
        && returnUrl.indexOf('\\') < 0
        && returnUrl.chars().noneMatch(Character::isISOControl)
        && returnUrl.startsWith(DAILY_VIEW);
  }

  /** The return target if it is safe, otherwise {@code fallback}, which may be {@code null}. */
  static String orElse(String returnUrl, String fallback) {
    return isSafe(returnUrl) ? returnUrl : fallback;
  }

}
