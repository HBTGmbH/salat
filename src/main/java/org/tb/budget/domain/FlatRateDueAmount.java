package org.tb.budget.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One amount of a flat rate falling due on one day (#972).
 *
 * <p>This, not the definition, is what the controlling allocates: a monthly flat rate running across
 * two budget plans has its January amount count against the first and its September amount against
 * the second. Allocating the definition as a whole would force one of the two to swallow the other's
 * months.
 */
public record FlatRateDueAmount(OrderFlatRate flatRate, LocalDate due, BigDecimal amount) {

}
