package de.hbt.salat.beta.viewhelper;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import de.hbt.salat.beta.domain.BetaFeature;
import de.hbt.salat.beta.domain.SwitchOffQuestion;
import de.hbt.salat.beta.service.BetaFeatureService;
import de.hbt.salat.beta.service.BetaFeedbackService;

/**
 * Exposes the current user's beta flags to Thymeleaf.
 *
 * <p>Deliberately a view helper and not a model attribute: the markup guarded by these flags is
 * rendered from several independent entry points — full page loads plus the HTMX fragment endpoints
 * {@code refresh-orders}, {@code refresh-sidebar}, {@code update-inline} and the out-of-band swap of
 * {@code #workingday-form}. A model attribute would have to be set in every one of those handlers,
 * and the flag would silently disappear from the next fragment endpoint somebody adds.
 *
 * <p>Request scoped with memoized values, so a page with many fragments still reads the preference
 * once.
 *
 * <p>There is currently no beta (see {@link BetaFeature}). A new one gets a named getter here that
 * delegates to {@link #isEnabled}, so templates read {@code @betaViewHelper.featureName} rather than
 * spelling out the enum.
 */
@Slf4j
@Component
@Scope(value = SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class BetaViewHelper {

  private final BetaFeatureService betaFeatureService;
  private final BetaFeedbackService betaFeedbackService;

  private final Map<BetaFeature, Boolean> resolved = new EnumMap<>(BetaFeature.class);

  public boolean isEnabled(BetaFeature feature) {
    return resolved.computeIfAbsent(feature, this::resolve);
  }

  /**
   * Whether the page of the beta asks how helpful it is (#1447): the fragment
   * {@code beta/feedback :: usePrompt} renders only then.
   */
  public boolean useFeedbackDue(String featureKey) {
    try {
      return betaFeedbackService.isUseFeedbackDue(featureKey);
    } catch (RuntimeException e) {
      log.debug("Could not decide on the feedback question of beta {}", featureKey, e);
      return false;
    }
  }

  /** The betas the person just switched off, to ask why on the settings page (#1447). */
  public List<SwitchOffQuestion> switchOffQuestions() {
    try {
      return betaFeedbackService.getSwitchOffQuestions();
    } catch (RuntimeException e) {
      log.debug("Could not read the switch-off questions", e);
      return List.of();
    }
  }

  private boolean resolve(BetaFeature feature) {
    try {
      return betaFeatureService.isEnabledForCurrentUser(feature);
    } catch (RuntimeException e) {
      // A beta flag must never be the reason a page fails to render.
      log.debug("Could not resolve beta feature {}, falling back to disabled", feature, e);
      return false;
    }
  }

}
