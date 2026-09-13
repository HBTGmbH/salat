package org.tb.budget.domain;

/**
 * One customer order in the segment listing (#779): the line the controlling view shows as the total
 * of that order, plus what it takes to name and open it.
 *
 * @param total the sum over every section of the order's evaluation
 *              (→ {@link BudgetControllingResult#total()}) — the same figures the order's own
 *              controlling page reports, which is what lets a reader follow the button and find the
 *              number again.
 */
public record SegmentControllingOrder(
    String customerorderSign,
    String customerorderDescription,
    String customerShortname,
    BudgetControllingRow total
) {

    /**
     * The line as the listing shows it. The evaluation's total carries no name — within its own page
     * it is simply "the total" — so the order's is put on it here: the sign above, the customer and
     * the short description below, the same two lines a section uses for its suborders.
     */
    public static SegmentControllingOrder of(String customerorderSign, String customerorderDescription,
                                             String customerShortname, BudgetControllingRow total) {
        return new SegmentControllingOrder(customerorderSign, customerorderDescription, customerShortname,
            total.toBuilder()
                .sign(customerorderSign)
                .label(label(customerorderDescription, customerShortname))
                .build());
    }

    private static String label(String description, String customerShortname) {
        if (customerShortname == null || customerShortname.isBlank()) {
            return description;
        }
        return description == null || description.isBlank()
            ? customerShortname
            : customerShortname + " - " + description;
    }
}
