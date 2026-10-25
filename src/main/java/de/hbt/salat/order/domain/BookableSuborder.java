package de.hbt.salat.order.domain;

/**
 * A suborder a contract may book on on a given day, with the employee order a booking there goes to (#1532) — as
 * values, for a module that offers booking without handling the order module's entities (ADR-0021).
 *
 * @param suborderId        the suborder
 * @param employeeorderId   the employee order valid on the day, the one a booking hangs on
 * @param completeOrderSign the sign of the whole path, e.g. {@code ABC-12/01}
 * @param description       the label the booking form shows
 * @param commentNecessary  whether a booking there needs a description
 */
public record BookableSuborder(long suborderId, long employeeorderId, String completeOrderSign, String description,
    boolean commentNecessary) {

  public static BookableSuborder of(Employeeorder employeeorder) {
    Suborder suborder = employeeorder.getSuborder();
    return new BookableSuborder(suborder.getId(), employeeorder.getId(), suborder.getCompleteOrderSign(),
        suborder.getCompleteOrderDescription(true, false), suborder.getCommentnecessary());
  }
}
