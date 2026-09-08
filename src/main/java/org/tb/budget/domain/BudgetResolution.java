package org.tb.budget.domain;

import java.util.List;
import java.util.Optional;

/**
 * The outcome of resolving a booking to a budget plan. Three outcomes have to stay distinguishable:
 * exactly one plan covers the booking, several do, or none does. Only the first one is ever
 * assigned automatically — with several candidates it is not decidable which plan is meant, and
 * guessing would count a booking against a budget nobody chose (#909).
 *
 * <p>The distinction between "several" and "none" is not cosmetic: the initial assignment of the
 * existing stock (#910) and the bulk assignment (#911) report the two cases differently, because
 * only one of them can be fixed by creating a plan.
 */
public record BudgetResolution(List<OrderBudget> candidates) {

    public BudgetResolution {
        candidates = List.copyOf(candidates);
    }

    /** Exactly one plan covers the booking — the only case in which it may be assigned. */
    public boolean isUnique() {
        return candidates.size() == 1;
    }

    /** Several plans cover the booking, so which one is meant cannot be decided. */
    public boolean isAmbiguous() {
        return candidates.size() > 1;
    }

    /** No plan covers the booking; it belongs to no budget. */
    public boolean isEmpty() {
        return candidates.isEmpty();
    }

    /** The single covering plan, empty in both other cases. */
    public Optional<OrderBudget> unique() {
        return isUnique() ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

}
