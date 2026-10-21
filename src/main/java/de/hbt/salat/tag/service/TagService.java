package de.hbt.salat.tag.service;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.tag.domain.Tag;
import de.hbt.salat.tag.domain.TagName;
import de.hbt.salat.tag.domain.TagOption;
import de.hbt.salat.tag.persistence.TagRepository;

/**
 * The tags themselves (#847, → ADR-0042): which there are, and turning names into tags. Which object carries which
 * tag is not known here; the owning module keeps that in its own join table and takes the tags as references by the
 * ids this service hands out.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Authorized(requireUnrestricted = true)
public class TagService {

  private final TagRepository tagRepository;

  /** Every tag, by name regardless of case — few enough to be offered in full. */
  @Transactional(readOnly = true)
  public List<TagOption> getTagOptions() {
    return tagRepository.findAllByOrderByNameKeyAsc().stream().map(TagOption::from).toList();
  }

  /**
   * The ids of the tags with these names, a tag created for every name that has none yet. Names are normalized
   * ({@link TagName}); names that differ only in case or white space are one tag, also within the same call, and an
   * empty name is dropped.
   *
   * <p>Management only, as the editing of the objects that carry tags: whoever may tag creates tags as well
   * (#847). A further kind of object with another role says so here.
   *
   * @return the ids in the order of the names, each once
   * @throws de.hbt.salat.common.exception.InvalidDataException {@code TG-0001}/{@code TG-0002} for a name that
   *     cannot be stored
   */
  @Authorized(requiresManager = true)
  public List<Long> resolveTagIds(Collection<String> names) {
    var wanted = new LinkedHashMap<String, TagName>();
    names.stream().map(TagName::of).filter(Objects::nonNull).forEach(name -> wanted.putIfAbsent(name.key(), name));
    if (wanted.isEmpty()) {
      return List.of();
    }
    var existing = tagRepository.findAllByNameKeyIn(wanted.keySet()).stream()
        .collect(toMap(Tag::getNameKey, identity()));
    return wanted.values().stream()
        .map(name -> existing.computeIfAbsent(name.key(), key -> tagRepository.save(new Tag(name))))
        .map(Tag::getId)
        .toList();
  }
}
