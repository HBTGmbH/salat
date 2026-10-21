package de.hbt.salat.order.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.order.domain.TagChoice;
import de.hbt.salat.tag.domain.TagColor;
import de.hbt.salat.tag.domain.TagName;

/**
 * What the order and suborder controllers share about tags (#847): the tag filter of a list, and the choice of a
 * form.
 */
final class OrderTags {

  private OrderTags() {
  }

  /**
   * The tag ids of a list filter. UiState keeps one value per parameter, so the filter carries them comma-separated in
   * one hidden field, as the ticket list does with its types; whatever is not an id is ignored.
   */
  static List<Long> filterIds(String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(part -> part.matches("\\d{1,18}"))
        .map(Long::valueOf)
        .distinct()
        .toList();
  }

  /** The ids again as the hidden field carries them. */
  static String filterValue(List<Long> ids) {
    return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  /**
   * The tags a form offers: every tag, and a name of the form that is no tag yet — typed and not saved because
   * something else in the form was wrong. Without it the select would drop the name on the way back.
   */
  static List<TagChoice> formChoices(List<TagChoice> tags, List<String> formNames) {
    var known = tags.stream().map(tag -> tag.name().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    var choices = new ArrayList<>(tags);
    if (formNames != null) {
      formNames.stream()
          .filter(Objects::nonNull)
          .map(name -> name.strip().replaceAll("\\s+", " "))
          .filter(name -> !name.isEmpty())
          .filter(name -> known.add(name.toLowerCase(Locale.ROOT)))
          .map(name -> new TagChoice(0, name, colorOf(name), 0, 0))
          .forEach(choices::add);
    }
    return choices;
  }

  /** The colour the name will get; none for a name the service refuses anyway (too long, an emoji). */
  private static TagColor colorOf(String name) {
    try {
      return TagColor.of(TagName.of(name));
    } catch (InvalidDataException e) {
      return null;
    }
  }
}
