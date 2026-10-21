package de.hbt.salat.dailyreport.domain;

import java.util.List;
import de.hbt.salat.order.domain.TaggedTrees;

/**
 * The tags the booking list is filtered by (#847), and — once the service has asked the order module — where they
 * sit in the order trees. A booking matches when its order or its suborder lies in one of those trees: a tagged order
 * stands for its whole tree, a tagged suborder for everything below it, as a chosen order or suborder does.
 *
 * @param tagIds           the chosen tags, alternatives to each other; empty for no restriction
 * @param customerOrderIds the orders carrying one of them — filled by {@link #resolvedTo}
 * @param suborderIds      the suborders carrying one of them and everything below — filled by {@link #resolvedTo}
 */
public record TagSelection(List<Long> tagIds, List<Long> customerOrderIds, List<Long> suborderIds) {

  public static final TagSelection NONE = of(List.of());

  public TagSelection {
    tagIds = List.copyOf(tagIds);
    customerOrderIds = List.copyOf(customerOrderIds);
    suborderIds = List.copyOf(suborderIds);
  }

  /** The chosen tags, not resolved yet. */
  public static TagSelection of(List<Long> tagIds) {
    return new TagSelection(tagIds, List.of(), List.of());
  }

  public TagSelection resolvedTo(TaggedTrees trees) {
    return new TagSelection(tagIds, trees.customerorderIds(), trees.suborderIds());
  }

  /** Whether the list is filtered by tags at all — if so and nothing carries them, nothing matches. */
  public boolean active() {
    return !tagIds.isEmpty();
  }
}
