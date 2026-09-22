package org.tb.budget.domain;

import java.util.List;
import java.util.stream.Stream;

/**
 * What a budget plan select may offer (#1065), and which of its entries is only there because the
 * form already holds it.
 *
 * <p>The narrowing conditions are evaluated against what the form currently says, and that changes
 * while it is being filled in: moving the validity or the suborder can take the plan the record
 * stores out of the offered set. Dropping it from the select would be the failure #1005 describes —
 * a select whose value is missing from its options marks nothing, the browser falls back to the
 * first entry, and the next save writes <em>that</em>. Here it would silently unbind a rate whose
 * condition somebody negotiated.
 *
 * <p>So the held plan stays, and it says that it no longer fits. Whether the combination is allowed
 * is not decided here but when saving — which refuses it by name ({@code BU-0028}, {@code BU-0029}).
 * What is <em>not</em> kept is a plan the user may not see: authorization is no narrowing condition
 * but a boundary, and a boundary has no exceptions.
 *
 * @param plans        the options, the fitting ones first
 * @param notFittingId the id of the entry that does not fit the current entry — inactive, out of
 *                     scope or out of period — or {@code null} where every option fits
 */
public record SelectablePlans(List<OrderBudget> plans, Long notFittingId) {

    public static SelectablePlans none() {
        return new SelectablePlans(List.of(), null);
    }

    /**
     * The options of a select: the plans that fit, plus the one the form holds where the narrowing
     * has dropped it. Appended rather than sorted in, so the entries one can actually pick come
     * first.
     *
     * @param fitting    the plans that satisfy every narrowing condition, in display order
     * @param authorized every plan of the order the user may see — where the held one is looked up
     * @param heldPlanId what the form currently holds, or {@code null}
     */
    public static SelectablePlans of(List<OrderBudget> fitting, List<OrderBudget> authorized,
                                     Long heldPlanId) {
        if (heldPlanId == null || fitting.stream().anyMatch(plan -> heldPlanId.equals(plan.getId()))) {
            return new SelectablePlans(fitting, null);
        }
        return authorized.stream()
            .filter(plan -> heldPlanId.equals(plan.getId()))
            .findFirst()
            .map(held -> new SelectablePlans(
                Stream.concat(fitting.stream(), Stream.of(held)).toList(), held.getId()))
            // The held plan belongs to another customer order — switching the order drops it, the
            // way it drops the suborder, and no message is needed for a change that plain.
            .orElse(new SelectablePlans(fitting, null));
    }

    public boolean isEmpty() {
        return plans.isEmpty();
    }

}
