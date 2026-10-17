package de.hbt.salat.order.persistence;

import static de.hbt.salat.common.util.ContainsPattern.like;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import de.hbt.salat.customer.domain.Customer_;
import de.hbt.salat.order.domain.Customerorder_;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.Suborder_;

/** The search text over a suborder, shared by the suborder and the employee order list (#1380). */
final class SuborderSearchText {

  private SuborderSearchText() {
  }

  /**
   * The search text of the suborder and employee order lists (#1380), in the query rather than over the loaded rows:
   * customer, order, the complete sign and the descriptions — of the suborder itself and of every suborder above it,
   * which the lists show in {@link Suborder#getCompleteOrderDescription}. A suborder lies above another of the same
   * order if the complete sign of the other starts with its own and a slash; compared as a substring, not with
   * {@code like}, so that an underscore in a sign stands for itself. The description is a {@code @Lob}, which
   * Hibernate passes to no {@code lower} (see {@link SuborderRepository#SHORTDESCRIPTION_AS_SHOWN}); {@code concat}
   * makes a string of it.
   */
  static Predicate containing(From<?, Suborder> suborder, CriteriaQuery<?> query, CriteriaBuilder builder,
      String pattern) {
      var order = suborder.join(Suborder_.customerorder);
      var customer = order.join(Customerorder_.customer);
      var above = query.subquery(Integer.class);
      var parent = above.from(Suborder.class);
      var parentSign = parent.get(Suborder_.completeOrderSign);
      above.select(builder.literal(1)).where(
          builder.equal(parent.get(Suborder_.customerorder), suborder.get(Suborder_.customerorder)),
          builder.equal(
              builder.substring(suborder.get(Suborder_.completeOrderSign), builder.literal(1),
                  builder.sum(builder.length(parentSign), 1)),
              builder.concat(parentSign, "/")),
          builder.or(
              like(builder, parent.get(Suborder_.shortdescription), pattern),
              like(builder, builder.concat(parent.get(Suborder_.description), ""), pattern)));
      return builder.or(
          like(builder, customer.get(Customer_.shortname), pattern),
          like(builder, customer.get(Customer_.name), pattern),
          like(builder, order.get(Customerorder_.sign), pattern),
          like(builder, order.get(Customerorder_.shortdescription), pattern),
          like(builder, order.get(Customerorder_.description), pattern),
          like(builder, suborder.get(Suborder_.completeOrderSign), pattern),
          like(builder, suborder.get(Suborder_.shortdescription), pattern),
          like(builder, builder.concat(suborder.get(Suborder_.description), ""), pattern),
          builder.exists(above));
  }
}
