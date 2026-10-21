package de.hbt.salat.order.service;

import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.order.domain.TagChoice;
import de.hbt.salat.order.domain.TagUsage;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.SuborderRepository;
import de.hbt.salat.tag.service.TagService;

/**
 * The tags as the order module offers them (#847): every tag, with how many orders and suborders carry it. The
 * module {@code tag} cannot count — it does not know who carries a tag; the join tables are this module's.
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

  private static Map<Long, Long> byTag(List<TagUsage> usages) {
    return usages.stream().collect(toMap(TagUsage::tagId, TagUsage::count));
  }
}
