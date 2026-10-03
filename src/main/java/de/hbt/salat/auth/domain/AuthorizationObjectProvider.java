package de.hbt.salat.auth.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
   * What the module makes of a stored object id. The provider knows its format and its data; the editor knows neither.
   *
   * <p>The wildcard {@code *} never gets here: "applies to every object" is a statement of the auth module, not of the
   * provider. Composite wildcards such as {@code E12:*} are another matter — only the provider knows them, and its
   * judgement has to let them pass, or exactly the form #1089 introduced the format for stays out of reach.
   *
   * <p>The default judges by {@link #describe}: what it knows is valid, anything else is unknown but not malformed — the
   * thing a stored id names may have been deleted since, and the rule stays readable.
   */
  default ObjectJudgement judge(String objectId) {
    if (objects().isEmpty()) {
      return ObjectJudgement.VALID;
    }
    return describe(List.of(objectId)).containsKey(objectId) ? ObjectJudgement.VALID : ObjectJudgement.UNKNOWN;
  }

  /**
   * What the editor shows for stored object ids — also for one the list no longer offers, such as a person since
   * hidden (#1204). An id nothing answers to any more is left out of the answer. The default looks the ids up in the
   * list; a provider whose list leaves something out answers itself.
   */
  default Map<String, AuthorizationObject> describe(Collection<String> objectIds) {
    return objects().stream()
        .filter(object -> objectIds.contains(object.id()))
        .collect(Collectors.toMap(AuthorizationObject::id, Function.identity(), (a, b) -> a));
  }

  /**
   * Translates what somebody typed into the object id that is stored (#1204) — asked only for a category that cannot be
   * enumerated, where the field is free text. People type what they know, a sign or an order sign; the rule stores the
   * id, so that a renamed record keeps its rules. Empty where nothing answers to the input: an id cannot precede the
   * record it names.
   */
  default Optional<String> objectIdOf(String input) {
    return Optional.of(input);
  }

}
