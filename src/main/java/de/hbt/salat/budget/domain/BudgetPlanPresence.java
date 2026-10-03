package de.hbt.salat.budget.domain;

/**
 * Whether a customer order has budget plans, and an active one among them (#1157).
 *
 * @param active the largest of the plans' active flags as 1 or 0 — a {@code max} over a
 *               {@code case}, which JPQL answers as a number
 */
public record BudgetPlanPresence(Long customerorderId, Integer active) {

  public boolean hasActivePlan() {
    return active != null && active > 0;
  }
}
