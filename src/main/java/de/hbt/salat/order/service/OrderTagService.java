package de.hbt.salat.order.service;

import static java.util.stream.Collectors.toMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.order.domain.TagChoice;
import de.hbt.salat.order.domain.TagUsage;
import de.hbt.salat.order.domain.TaggedSuborder;
import de.hbt.salat.order.domain.TaggedTrees;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.SuborderRepository;
import de.hbt.salat.tag.service.TagService;

/**
 * The tags as the order module offers them (#847): every tag, with how many orders and suborders carry it, and where
 * tags sit in the order trees. The module {@code tag} cannot answer either — it does not know who carries a tag; the
 * join tables are this module's.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized(requireUnrestricted = true)
public class OrderTagService {

  private final TagService tagService;
  private final CustomerorderRepository customerorderRepository;
  private final SuborderRepository suborderRepository;

  /** Every tag by name, three queries whatever their number. */
  public List<TagChoice> getTagChoices() {
    var customerorders = byTag(customerorderRepository.findTagUsages());
    var suborders = byTag(suborderRepository.findTagUsages());
    return tagService.getTagOptions().stream()
        .map(tag -> new TagChoice(tag.id(), tag.name(), tag.color(),
            customerorders.getOrDefault(tag.id(), 0L), suborders.getOrDefault(tag.id(), 0L)))
        .toList();
  }

  /**
   * Where these tags sit in the order trees (#847): the orders carrying one, and the suborders carrying one with
   * everything below them. Three queries whatever the number of tags — the descendants come from the edges of the
   * orders concerned, not from loading suborder after suborder.
   */
  public TaggedTrees getTaggedTrees(Collection<Long> tagIds) {
    if (tagIds.isEmpty()) {
      return new TaggedTrees(List.of(), List.of());
    }
    var customerorderIds = customerorderRepository.findIdsByTagIds(tagIds);
    var tagged = suborderRepository.findTagged(tagIds);
    if (tagged.isEmpty()) {
      return new TaggedTrees(customerorderIds, List.of());
    }
    var children = new HashMap<Long, List<Long>>();
    var orders = tagged.stream().map(TaggedSuborder::customerorderId).collect(Collectors.toSet());
    for (var edge : suborderRepository.findEdgesByCustomerorderIds(orders)) {
      if (edge.parentId() != null) {
        children.computeIfAbsent(edge.parentId(), parent -> new ArrayList<>()).add(edge.id());
      }
    }
    var suborderIds = new LinkedHashSet<Long>();
    var pending = new ArrayDeque<Long>();
    tagged.forEach(suborder -> pending.add(suborder.id()));
    while (!pending.isEmpty()) {
      var id = pending.poll();
      if (suborderIds.add(id)) {
        pending.addAll(children.getOrDefault(id, List.of()));
      }
    }
    return new TaggedTrees(customerorderIds, List.copyOf(suborderIds));
  }

  private static Map<Long, Long> byTag(List<TagUsage> usages) {
    return usages.stream().collect(toMap(TagUsage::tagId, TagUsage::count));
  }
}
