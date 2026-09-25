package org.tb.common;

import static java.lang.Boolean.TRUE;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.jpa.domain.Specification;

/**
 * Whether a record is hidden — the one place that rule is written down in code (#1104).
 *
 * <p>{@code hide} is the manual decision to take a record out of the select boxes, independent of
 * any date (see AGENTS.md, „The {@code hide} Flag", → ADR-0012). The column is {@code bit(1)} with
 * default {@code false} but <em>without</em> {@code NOT NULL}, so it has three states, and the
 * middle one is the trap:
 *
 * <ul>
 *   <li>{@code true} — hidden.</li>
 *   <li>{@code false} — not hidden.</li>
 *   <li>{@code null} — <strong>not hidden</strong>. Nobody hid such a record; the flag was simply
 *       never written. Every {@code getHide()} / {@code isHide()} in the application reads it that
 *       way, and so must every query.</li>
 * </ul>
 *
 * <p>Both sides of that one question live here side by side, so that a list and the row it renders
 * cannot disagree about the same record:
 *
 * <pre>{@code
 * // in Java, on the entity
 * public Boolean getHide() {
 *     return Hiding.isHidden(hide);
 * }
 *
 * // in the query, in the DAO
 * if (!TRUE.equals(showHidden)) {
 *     predicates.add(Hiding.<Suborder>notHidden(Suborder_.hide).toPredicate(root, query, builder));
 * }
 * }</pre>
 *
 * <p><strong>Never write {@code notEqual(hide, TRUE)} or {@code hide != true}.</strong> Both
 * translate to {@code hide <> 1}, and in SQL that comparison is <em>unknown</em> for {@code NULL}
 * rather than true — the row drops out of the result, although nobody hid it. That was the split
 * this class closed: seven places said "hidden", five and all the entities said "not hidden".
 *
 * <p><strong>In JPQL the spelling has to be repeated</strong>, because a {@code @Query} is a string
 * constant and cannot call this class. There is exactly one spelling, and it is this one:
 * {@code (x.hide is null or x.hide = false)}. It has to stay in step with {@link #notHidden}.
 *
 * <p>This is a different question from the validity period — see {@link Validity} and ADR-0029.
 * {@code notHidden} and {@code notInactive} are two predicates behind two switches; they never
 * belong in one condition.
 */
public final class Hiding {

  private Hiding() {
  }

  /**
   * Whether the record carrying this flag is hidden. {@code null} is not hidden.
   */
  public static boolean isHidden(@Nullable Boolean hide) {
    return TRUE.equals(hide);
  }

  /**
   * The query-side twin of {@link #isHidden(Boolean)} for an entity owning the flag itself.
   */
  public static <E> Specification<E> notHidden(SingularAttribute<? super E, Boolean> hide) {
    return (root, query, builder) -> notHidden(builder, root.get(hide));
  }

  /**
   * The same predicate over an arbitrary path — for an entity that has no {@code hide} of its own
   * and inherits it from a parent, which it reaches over a join ({@code Employeeorder}).
   */
  public static Predicate notHidden(CriteriaBuilder builder, Expression<Boolean> hide) {
    return builder.or(builder.isNull(hide), builder.isFalse(hide));
  }
}
