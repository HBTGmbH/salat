package org.tb.settings.viewhelper;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.tb.settings.domain.BetaFeature;
import org.tb.settings.service.BetaFeatureService;

/**
 * Exposes the current user's beta flags to Thymeleaf.
 *
 * <p>Deliberately a view helper and not a model attribute: the markup guarded by these flags is
 * rendered from several independent entry points — full page loads plus the HTMX fragment endpoints
 * {@code refresh-orders}, {@code refresh-sidebar}, {@code update-inline} and the out-of-band swap of
 * {@code #workingday-form}. A model attribute would have to be set in every one of those handlers,
 * and the flag would silently disappear from the next fragment endpoint somebody adds.
 *
 * <p>Request scoped with a memoized value, so a page with many fragments still reads the preference
 * once.
 */
@Slf4j
@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class BetaViewHelper {

  private final BetaFeatureService betaFeatureService;

  private Boolean timeInput;

  /** #830 — new stepper based input for time and duration fields. */
  public boolean isTimeInput() {
    if (timeInput == null) {
      timeInput = isEnabled(BetaFeature.TIME_INPUT);
    }
    return timeInput;
  }

  private boolean isEnabled(BetaFeature feature) {
    try {
      return betaFeatureService.isEnabledForCurrentUser(feature);
    } catch (RuntimeException e) {
      // A beta flag must never be the reason a page fails to render.
      log.debug("Could not resolve beta feature {}, falling back to disabled", feature, e);
      return false;
    }
  }

}
