package org.tb.auth.domain;

import java.util.List;

/**
 * What a module contributes about its own authorization category, so that the rule editor can offer it (#1074).
 *
 * <p>The auth module must not know that ETL definitions or reports exist — it may only import {@code common}. Each
 * module implements this interface for its own category instead, and Spring hands the editor every implementation as
 * a list. The dependency thus points the way it already does: {@code etl}, {@code reporting}, {@code employee} and
 * {@code dailyreport} know {@code auth}, and {@code auth} knows none of them. The same pattern as
 * {@code UiStateKeyContributor}.
 */
public interface AuthorizationObjectProvider {

  /** The category as it is written into a rule, e.g. {@code ETL}. */
  String category();

  /** Message key for the display name of the category. */
  String labelKey();

  /**
   * Message key for the text at the object field saying what belongs there. A free field without that sentence is a
   * guessing game, and what gets guessed is a rule that never fires.
   */
  String objectHintKey();

  /**
   * The selectable objects, or empty where the category cannot be enumerated. Empty means free text — the editor asks
   * the same question either way and does not hang on whether a category happens to be enumerable.
   */
  List<AuthorizationObject> objects();

  /**
   * What the module makes of a typed object id. The provider knows its format and its data; the editor knows neither.
   *
   * <p>The wildcard {@code *} never gets here: "applies to every object" is a statement of the auth module, not of the
   * provider. Composite wildcards such as {@code xx:*} are another matter — only the provider knows them, and its
   * judgement has to let them pass, or exactly the form #1089 introduced the format for stays out of reach.
   *
   * <p>The default judges by the list: what is in it is valid, anything else is unknown but not malformed. A rule may
   * precede the thing it is about — an ETL definition that arrives next week — so an unknown value is noted, not
   * refused.
   */
  default ObjectJudgement judge(String objectId) {
    if (objects().isEmpty()) {
      return ObjectJudgement.VALID;
    }
    return objects().stream().anyMatch(object -> object.id().equals(objectId))
        ? ObjectJudgement.VALID
        : ObjectJudgement.UNKNOWN;
  }

}
