package org.tb.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.common.GlobalConstants;
import org.tb.common.domain.AuditedEntity;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;

/**
 * The one resolution behind the "Mitarbeitende" card and the rate columns of the booking list
 * (#964). What is pinned here is the distinction the whole ticket turns on: a resolution that found
 * nothing is a finding, an amount of 0 EUR is not, and a suborder that is not invoiceable is
 * neither.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class AppliedRateLookupTest {

  private static final LocalDate DAY = LocalDate.of(2026, 6, 15);
  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 12, 31);

  private static final long SUBORDER_ID = 7L;

  @Test
  public void names_the_cost_category_and_the_condition_that_apply() {
    var rate = lookup(suborder(true, OrderType.STANDARD),
        costLookup("Senior", 9500), pricingLookup(14000))
        .resolve("abc", SUBORDER_ID, DAY);

    assertThat(rate.costName()).isEqualTo("Senior");
    assertThat(rate.costCentsPerHour()).isEqualTo(9500);
    assertThat(rate.costEuroPerHour()).isEqualByComparingTo("95.00");
    assertThat(rate.priceCentsPerHour()).isEqualTo(14000);
    assertThat(rate.priceEuroPerHour()).isEqualByComparingTo("140.00");
    assertThat(rate.missingCost()).isFalse();
    assertThat(rate.missingPrice()).isFalse();
  }

  /** Without an assignment the work costs 0 EUR in controlling — silently, until now. */
  @Test
  public void reports_a_missing_cost_rate() {
    var rate = lookup(suborder(true, OrderType.STANDARD),
        EmployeeCostLookup.of(List.of(), List.of()), pricingLookup(14000))
        .resolve("abc", SUBORDER_ID, DAY);

    assertThat(rate.hasCost()).isFalse();
    assertThat(rate.missingCost()).isTrue();
  }

  @Test
  public void reports_a_missing_condition() {
    var rate = lookup(suborder(true, OrderType.STANDARD),
        costLookup("Senior", 9500), OrderPricingLookup.of(List.of()))
        .resolve("abc", SUBORDER_ID, DAY);

    assertThat(rate.hasPrice()).isFalse();
    assertThat(rate.missingPrice()).isTrue();
  }

  /**
   * The point of the distinction: 0,00 EUR/h is a stored, deliberate statement — unpaid work,
   * goodwill, settled elsewhere — and must not look like a condition nobody entered.
   */
  @Test
  public void treats_a_condition_of_zero_euro_as_a_condition() {
    var rate = lookup(suborder(true, OrderType.STANDARD),
        costLookup("Senior", 9500), pricingLookup(0))
        .resolve("abc", SUBORDER_ID, DAY);

    assertThat(rate.hasPrice()).isTrue();
    assertThat(rate.priceEuroPerHour()).isEqualByComparingTo("0.00");
    assertThat(rate.missingPrice()).isFalse();
  }

  /** 0 EUR revenue is right there, whatever rate matches — so it is named, not faulted. */
  @Test
  public void does_not_fault_a_missing_condition_on_a_suborder_that_is_not_invoiceable() {
    var rate = lookup(suborder(false, OrderType.STANDARD),
        costLookup("Senior", 9500), OrderPricingLookup.of(List.of()))
        .resolve("abc", SUBORDER_ID, DAY);

    assertThat(rate.notInvoiceable()).isTrue();
    assertThat(rate.missingPrice()).isFalse();
  }

  /** Costs accrue whether or not the work is billed, so the cost side is judged as usual there. */
  @Test
  public void still_reports_a_missing_cost_rate_on_a_suborder_that_is_not_invoiceable() {
    var rate = lookup(suborder(false, OrderType.STANDARD),
        EmployeeCostLookup.of(List.of(), List.of()), pricingLookup(14000))
        .resolve("abc", SUBORDER_ID, DAY);

    assertThat(rate.missingCost()).isTrue();
  }

  /** A page that does not report costs must not read as a page on which no cost rate applies. */
  @Test
  public void reports_no_cost_at_all_where_costs_are_not_included() {
    var lookup = AppliedRateLookup.of("co", List.of(suborder(true, OrderType.STANDARD)),
        null, pricingLookup(14000));

    var rate = lookup.resolve("abc", SUBORDER_ID, DAY);

    assertThat(lookup.includesCosts()).isFalse();
    assertThat(rate.hasCost()).isFalse();
    assertThat(rate.missingCost()).isFalse();
    assertThat(rate.hasPrice()).isTrue();
  }

  /** Standby is paid differently from the work a general rate stands for (#463). */
  @Test
  public void takes_no_general_cost_assignment_for_a_standby_suborder() {
    var rate = lookup(suborder(true, OrderType.BEREITSCHAFT),
        costLookup("Senior", 9500), pricingLookup(14000))
        .resolve("abc", SUBORDER_ID, DAY);

    assertThat(rate.missingCost()).isTrue();
  }

  /**
   * A booking whose suborder could not be read at all. Inventing a rate for it would be the worse
   * answer, so both sides stay empty — and the cost side stays quiet where costs are not reported.
   */
  @Test
  public void resolves_nothing_for_an_unknown_suborder() {
    var lookup = AppliedRateLookup.of("co", List.of(), costLookup("Senior", 9500), pricingLookup(14000));

    var rate = lookup.resolve("abc", SUBORDER_ID, DAY);

    assertThat(rate.hasCost()).isFalse();
    assertThat(rate.hasPrice()).isFalse();
    assertThat(rate.missingCost()).isTrue();
    assertThat(rate.missingPrice()).isTrue();
  }

  /** The complete order sign — the chain of parents — is what both lookups are keyed by. */
  @Test
  public void resolves_by_the_complete_order_sign_of_the_suborder() {
    var suborder = suborder(true, OrderType.STANDARD);
    var onOtherSign = new OrderPricing();
    onOtherSign.setCustomerorderSign("co");
    onOtherSign.setSuborderSign("co/99");
    onOtherSign.setPriceCentsPerHour(99900);
    onOtherSign.setValidFrom(FROM);
    onOtherSign.setValidUntil(UNTIL);

    var rate = AppliedRateLookup.of("co", List.of(suborder), costLookup("Senior", 9500),
        OrderPricingLookup.of(List.of(onOtherSign))).resolve("abc", SUBORDER_ID, DAY);

    assertThat(suborder.getCompleteOrderSign()).isEqualTo("co/01");
    assertThat(rate.hasPrice()).isFalse();
  }

  private static AppliedRateLookup lookup(Suborder suborder, EmployeeCostLookup costs,
                                          OrderPricingLookup pricings) {
    return AppliedRateLookup.of("co", List.of(suborder), costs, pricings);
  }

  private static EmployeeCostLookup costLookup(String name, int centsPerHour) {
    var assignment = new EmployeeCostAssignment();
    assignment.setEmployeeSign("abc");
    assignment.setEmployeeCostName(name);
    assignment.setValidFrom(FROM);
    assignment.setValidUntil(UNTIL);

    var cost = new EmployeeCost();
    cost.setName(name);
    cost.setCostCentsPerHour(centsPerHour);
    cost.setValidFrom(FROM);
    cost.setValidUntil(UNTIL);

    return EmployeeCostLookup.of(List.of(assignment), List.of(cost));
  }

  private static OrderPricingLookup pricingLookup(int centsPerHour) {
    var pricing = new OrderPricing();
    pricing.setCustomerorderSign("co");
    pricing.setPriceCentsPerHour(centsPerHour);
    pricing.setValidFrom(FROM);
    pricing.setValidUntil(UNTIL);
    return OrderPricingLookup.of(List.of(pricing));
  }

  private static Suborder suborder(boolean invoiceable, OrderType orderType) {
    var customerorder = new Customerorder();
    customerorder.setSign("co");
    customerorder.setOrderType(OrderType.STANDARD);

    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign("01");
    suborder.setInvoice(invoiceable ? GlobalConstants.INVOICE_YES : 'N');
    suborder.setOrderType(orderType);
    setId(suborder, SUBORDER_ID);
    return suborder;
  }

  /** The id is generated, so there is no setter; a stored record always has one. */
  private static void setId(AuditedEntity entity, long id) {
    try {
      Field field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
  }

}
