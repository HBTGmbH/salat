package de.hbt.salat.order.persistence;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.tag.domain.Tag;

/**
 * References to the tags of an order or suborder, from the ids {@code TagService.resolveTagIds} hands out (#847).
 *
 * <p>A reference rather than the loaded entity, and taken here rather than from the service of the module
 * {@code tag}: that service would hand an entity across the module boundary (ADR-0021,
 * {@code ArchitectureTest.noEntityCrossesAModuleBoundaryThroughAService}). The reference only carries the id into
 * the join table; the ids come from the service, which has just found or created the tags.
 */
@Component
@RequiredArgsConstructor
public class TagReferences {

  private final EntityManager entityManager;

  public List<Tag> tags(Collection<Long> tagIds) {
    return tagIds.stream().map(id -> entityManager.getReference(Tag.class, id)).toList();
  }
}
