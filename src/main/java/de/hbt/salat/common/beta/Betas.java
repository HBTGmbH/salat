package de.hbt.salat.common.beta;

/**
 * What a module asks about its betas and tells about their use (#1447). The beta module implements
 * it; a module reaches it through this interface alone and never imports the beta module.
 * Templates ask {@code @betaViewHelper.isEnabled('key')} instead.
 */
public interface Betas {

  /** Whether the logged-in person has switched the beta on. */
  boolean isEnabled(BetaFeature feature);

  /**
   * Counts a use of the beta — with it switched on and, for comparison, off. The event is the
   * module's own key ({@code favorite-applied}); the beta module makes no assumption about it beyond
   * its form. Call it from the controller once the action has been done; it never throws.
   */
  void count(BetaFeature feature, String event);
}
