package de.hbt.salat.jira.persistence;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * References to order and suborder for a replication whose scope arrives as ids from the form
 * (#1368).
 *
 * <p>A reference rather than the loaded entity, and taken here rather than from a service of the
 * order module: such a service would hand an entity across the module boundary (ADR-0021,
 * {@code ArchitectureTest.noEntityCrossesAModuleBoundaryThroughAService}). The reference only
 * carries the id into the foreign key, it loads nothing — whether order and suborder exist the
 * caller has already asked the order module.
 */
@Component
@RequiredArgsConstructor
public class OrderReferences {

  private final EntityManager entityManager;

  public Customerorder customerorder(long customerorderId) {
    return entityManager.getReference(Customerorder.class, customerorderId);
  }

  /** {@code null} for {@code null}: a replication without suborder covers the whole order. */
  public Suborder suborder(Long suborderId) {
    return suborderId == null ? null : entityManager.getReference(Suborder.class, suborderId);
  }
}
