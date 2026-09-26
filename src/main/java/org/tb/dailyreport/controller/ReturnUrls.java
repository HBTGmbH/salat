package org.tb.dailyreport.controller;

import java.util.Set;

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
 *   <li>it leads into the daily view, or it is one of the overviews before a release (#760) or an
 *       acceptance (#1122). The daily view is a prefix, as the controller had it before, so every
 *       target the application builds itself still passes, including the list view with
 *       {@code fMonth}/{@code fYear} and the {@code pathname + search} the share dialog sends. An
 *       overview is an exact path — what comes after it can only be the query or the fragment, never
 *       a further path segment.</li>
 * </ul>
 *
 * <p>The daily view offers a way back to an overview only (#760, {@link #isReviewPage}): that is
 * where its day links come from, and a target in the daily view itself would lead back to where the
 * user already is.
 */
final class ReturnUrls {

  private static final String DAILY_VIEW = "/dailyreport/daily";

  /**
   * The overviews before a release (#760) — of one's own contract and via the acceptance page — and
   * the one before an acceptance (#1122).
   */
  private static final Set<String> REVIEW_PAGES =
      Set.of("/release/review", "/acceptance/release/review", "/acceptance/accept/review");

  private ReturnUrls() {
  }

  static boolean isSafe(String returnUrl) {
    return isWellFormed(returnUrl)
        && (pathOf(returnUrl).startsWith(DAILY_VIEW) || REVIEW_PAGES.contains(pathOf(returnUrl)));
  }

  /** The target is safe and one of the overviews, not the daily view. */
  static boolean isReviewPage(String returnUrl) {
    return isWellFormed(returnUrl) && REVIEW_PAGES.contains(pathOf(returnUrl));
  }

  /** The return target if it is safe, otherwise {@code fallback}, which may be {@code null}. */
  static String orElse(String returnUrl, String fallback) {
    return isSafe(returnUrl) ? returnUrl : fallback;
  }

  private static boolean isWellFormed(String returnUrl) {
    return returnUrl != null
        && returnUrl.startsWith("/")
        && !returnUrl.startsWith("//")
        && returnUrl.indexOf('\\') < 0
        && returnUrl.chars().noneMatch(Character::isISOControl);
  }

  /** The path of the target: everything before its query or its fragment. */
  private static String pathOf(String returnUrl) {
    int end = returnUrl.length();
    for (char separator : new char[] {'?', '#'}) {
      int index = returnUrl.indexOf(separator);
      if (index >= 0 && index < end) {
        end = index;
      }
    }
    return returnUrl.substring(0, end);
  }

}
