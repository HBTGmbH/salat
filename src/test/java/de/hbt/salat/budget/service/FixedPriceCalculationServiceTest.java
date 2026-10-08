package de.hbt.salat.budget.service;

import static de.hbt.salat.testutils.ReferenceTestUtils.employeeWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.budget.auth.BudgetAuthorization;
import de.hbt.salat.budget.domain.CalculationLineData;
import de.hbt.salat.budget.domain.CostCategory;
import de.hbt.salat.budget.domain.EmployeeCost;
import de.hbt.salat.budget.domain.EmployeeCostAssignment;
import de.hbt.salat.budget.domain.FixedPriceEvaluation.Gap;
import de.hbt.salat.budget.domain.FlatRateRhythm;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.OrderBudgetAdjustment;
import de.hbt.salat.budget.domain.OrderBudgetCalculation;
import de.hbt.salat.budget.domain.OrderFlatRate;
import de.hbt.salat.budget.domain.OrderFlatRateLookup;
import de.hbt.salat.budget.domain.OrderPosition;
import de.hbt.salat.budget.domain.OrderPricing;
import de.hbt.salat.budget.domain.OrderPricingData;
import de.hbt.salat.budget.domain.PlanBooking;
import de.hbt.salat.budget.domain.ProgressStatus;
import de.hbt.salat.budget.domain.OrderBudgetScopeEntry;
import de.hbt.salat.budget.persistence.CostCategoryRepository;
import de.hbt.salat.budget.persistence.EmployeeCostAssignmentRepository;
import de.hbt.salat.budget.persistence.EmployeeCostRepository;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.persistence.OrderPricingRepository;
import de.hbt.salat.budget.persistence.TestMasterDataReferences;
import de.hbt.salat.budget.persistence.TimereportBudgetAssignmentRepository;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.order.domain.SuborderReadModel;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.testutils.CostCategoryTestUtils;

/**
 * Fixed-price plans as the service reads them (#1404, #1405): the category of a booking from the
 * cost assignment of its person on its day, the fixed price from the flat rates of the plan, and the
 * rules a line of the calculation has to keep.
 *
 * <p>The tree: {@code co/01}, {@code co/02} and {@code co/02/A} below it. The plan lives on
 * {@code co/02}.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class FixedPriceCalculationServiceTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);
  private static final long SENIOR_PERSON = 5L;
  private static final long PERSON_WITHOUT_COST = 6L;

  private final OrderTree tree = new OrderTree().with("co/01").with("co/02/A");

  private OrderBudgetRepository orderBudgetRepository;
  private TimereportBudgetAssignmentRepository assignmentRepository;
  private OrderPricingRepository orderPricingRepository;
  private CostCategoryRepository categoryRepository;
  private BudgetAuthorization budgetAuthorization;
  private OrderFlatRateService orderFlatRateService;
  private FixedPriceCalculationService service;
  private OrderBudget plan;
  private final List<PlanBooking> bookings = new ArrayList<>();

  @BeforeEach
  void setUp() {
    orderBudgetRepository = mock(OrderBudgetRepository.class);
    assignmentRepository = mock(TimereportBudgetAssignmentRepository.class);
    orderPricingRepository = mock(OrderPricingRepository.class);
    categoryRepository = mock(CostCategoryRepository.class);
    budgetAuthorization = mock(BudgetAuthorization.class);
    orderFlatRateService = mock(OrderFlatRateService.class);
    var costRepository = mock(EmployeeCostRepository.class);
    var costAssignmentRepository = mock(EmployeeCostAssignmentRepository.class);
    var suborderService = mock(SuborderService.class);
    tree.stub(mock(CustomerorderService.class), suborderService);
    when(suborderService.getAllSuborderReadModelsByCustomerorderId(anyLong())).thenAnswer(i -> readModels());
    when(suborderService.getSuborderReadModelsByCustomerorderId(anyLong())).thenAnswer(i -> readModels());

    // The senior works at 60 EUR/h; the other person has no cost assignment at all.
    var assignment = new EmployeeCostAssignment();
    assignment.setEmployee(employeeWithId(SENIOR_PERSON));
    assignment.setCategory(CostCategoryTestUtils.named("Senior"));
    assignment.setValidFrom(JAN);
    assignment.setValidUntil(DEC);
    var cost = new EmployeeCost();
    cost.setCategory(CostCategoryTestUtils.named("Senior"));
    cost.setCostCentsPerHour(6000);
    cost.setValidFrom(JAN);
    cost.setValidUntil(DEC);
    when(costAssignmentRepository.findAllByOrderByCategoryNameAscIdAsc()).thenReturn(List.of(assignment));
    when(costRepository.findAllByOrderByCategoryNameAscValidFromAsc()).thenReturn(List.of(cost));

    plan = new OrderBudget();
    setId(plan, 100L);
    plan.setName("Relaunch");
    plan.setCustomerorder(tree.order("co"));
    plan.setSuborder(tree.suborder("co/02"));
    plan.setValidFrom(JAN);
    plan.setValidUntil(DEC);
    plan.setActive(true);
    plan.setFixedPrice(true);
    when(orderBudgetRepository.findById(100L)).thenReturn(Optional.of(plan));
    when(orderBudgetRepository.findByCustomerorderId(anyLong())).thenAnswer(i -> List.of(plan));
    when(assignmentRepository.findPlanBookings(any(), any())).thenAnswer(i -> List.copyOf(bookings));
    when(orderFlatRateService.lookupFor(any())).thenReturn(OrderFlatRateLookup.of(List.of()));
    when(categoryRepository.findById(anyLong())).thenAnswer(i -> Optional.of(category(i.getArgument(0))));

    service = new FixedPriceCalculationService(orderBudgetRepository, assignmentRepository,
        orderPricingRepository, costRepository, costAssignmentRepository, categoryRepository, suborderService,
        orderFlatRateService, new OrderPositions(suborderService), budgetAuthorization,
        TestMasterDataReferences.create());
  }

  // --- evaluation ------------------------------------------------------------------------------

  /**
   * The category of a booking follows the cost assignment of its person on its day; a person without
   * one lands in the line "ohne Kostensatz" and still counts (#1404).
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  void sorts_the_bookings_into_the_calculation_by_the_category_of_their_person() {
    addLine("co/02", "Senior", 120);
    booked("co/02/A", SENIOR_PERSON, 30);
    booked("co/02", PERSON_WITHOUT_COST, 6);

    var evaluation = service.evaluate(plan, DEC, false).orElseThrow();

    assertThat(evaluation.rows()).hasSize(2);
    assertThat(evaluation.rows().get(0).categoryName()).isEqualTo("Senior");
    assertThat(evaluation.rows().get(0).bookedHours()).isEqualTo(Duration.ofHours(30));
    assertThat(evaluation.rows().get(1)).satisfies(row -> {
      assertThat(row.isWithoutCategory()).isTrue();
      assertThat(row.bookedHours()).isEqualTo(Duration.ofHours(6));
    });
    assertThat(evaluation.total().bookedHours()).isEqualTo(Duration.ofHours(36));
    assertThat(evaluation.consumedPercent()).isEqualTo(30.0);
  }

  /** Costs only where the caller may report them; the hours are for everybody who sees the plan. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  void prices_the_calculation_only_with_costs() {
    addLine("co/02", "Senior", 100);
    booked("co/02", SENIOR_PERSON, 10);

    var withCosts = service.evaluate(plan, DEC, true).orElseThrow();
    var withoutCosts = service.evaluate(plan, DEC, false).orElseThrow();

    assertThat(withCosts.total().calculatedCostEuro()).isEqualByComparingTo("6000.00");
    assertThat(withCosts.total().bookedCostEuro()).isEqualByComparingTo("600.00");
    assertThat(withoutCosts.total().calculatedCostEuro()).isNull();
    assertThat(withoutCosts.total().bookedCostEuro()).isNull();
  }

  /**
   * #1435: the fixed price is the sum of the plan's adjustments, whatever date each takes effect on.
   * The flat rates are what has been billed of it, counted up to today (#1436). The progress against
   * the consumption of the calculated hours decides the status.
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  void takes_the_fixed_price_from_the_adjustments_and_what_was_billed_from_the_flat_rates_due_by_today() {
    addLine("co/02", "Senior", 400);
    booked("co/02", SENIOR_PERSON, 160);
    adjustment("40000", JAN);
    adjustment("10000", LocalDate.of(2026, 9, 1));
    monthlyFlatRate("4000");
    progress(LocalDate.of(2026, 5, 31), 40);

    // The window reaches to the end of the year; the flat rates count up to 15.06. only.
    var evaluation = service.evaluate(plan, DEC, false).orElseThrow();

    assertThat(evaluation.fixedPriceEuro()).isEqualByComparingTo("50000");
    // six monthly amounts, January to June
    assertThat(evaluation.billedEuro()).isEqualByComparingTo("24000");
    assertThat(evaluation.progressStatus()).isEqualTo(ProgressStatus.ON_TRACK);
    assertThat(evaluation.calculatedRate().euroPerHour()).isEqualByComparingTo("125.00");
    // 160 h at 40 % project to 400 h
    assertThat(evaluation.expectedRateAtCompletion().euroPerHour()).isEqualByComparingTo("125.00");
  }

  /** Flat rates alone make no fixed price (#1435): without adjustments the rates have a gap. */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  void has_no_fixed_price_without_adjustments_however_many_flat_rates_there_are() {
    addLine("co/02", "Senior", 400);
    booked("co/02", SENIOR_PERSON, 160);
    monthlyFlatRate("4000");
    progress(LocalDate.of(2026, 5, 31), 40);

    var evaluation = service.evaluate(plan, DEC, false).orElseThrow();

    assertThat(evaluation.hasFixedPrice()).isFalse();
    assertThat(evaluation.calculatedRate().gap()).isEqualTo(Gap.NO_FIXED_PRICE);
    assertThat(evaluation.expectedRateAtCompletion().gap()).isEqualTo(Gap.NO_FIXED_PRICE);
    assertThat(evaluation.billedEuro()).isEqualByComparingTo("24000");
  }

  private void adjustment(String amount, LocalDate effective) {
    var adjustment = new OrderBudgetAdjustment();
    adjustment.setOrderBudget(plan);
    adjustment.setAmount(new BigDecimal(amount));
    adjustment.setEffective(effective);
    plan.getAdjustments().add(adjustment);
  }

  private void monthlyFlatRate(String amount) {
    var instalments = new OrderFlatRate();
    setId(instalments, 7L);
    instalments.setCustomerorder(tree.order("co"));
    instalments.setOrderBudget(plan);
    instalments.setRhythm(FlatRateRhythm.MONTHLY);
    instalments.setAmount(new BigDecimal(amount));
    instalments.setValidFrom(JAN);
    instalments.setValidUntil(DEC);
    when(orderFlatRateService.lookupFor(any())).thenReturn(OrderFlatRateLookup.of(List.of(instalments)));
  }

  /**
   * Dashboard and alert read the consumption through the same calculation as the plan's page, up to
   * today; a fixed price without calculated hours, and any other plan, is absent (#1404).
   */
  @Test
  @FixedClock("2026-06-15T10:00:00")
  void hands_dashboard_and_alert_the_consumption_the_evaluation_computes() {
    addLine("co/02", "Senior", 120);
    booked("co/02/A", SENIOR_PERSON, 30);
    booked("co/02", PERSON_WITHOUT_COST, 6);
    var withoutCalculation = new OrderBudget();
    setId(withoutCalculation, 101L);
    withoutCalculation.setFixedPrice(true);
    var serviceBudget = new OrderBudget();
    setId(serviceBudget, 102L);

    var consumed = service.getHoursConsumedPercents(List.of(plan, withoutCalculation, serviceBudget));

    assertThat(consumed).containsOnlyKeys(100L);
    assertThat(consumed.get(100L)).isEqualTo(service.evaluate(plan, LocalDate.of(2026, 6, 15), false)
        .orElseThrow().consumedPercent());
    assertThat(consumed.get(100L)).isEqualTo(30.0);
    verify(assignmentRepository).findPlanBookings(Set.of(100L),
        LocalDate.of(2026, 6, 15));
  }

  @Test
  void evaluates_nothing_for_a_plan_without_fixed_price() {
    plan.setFixedPrice(false);

    assertThat(service.evaluate(plan, DEC, false)).isEmpty();
  }

  /** Reading goes through the authorization of the plan like every other budget read. */
  @Test
  void refuses_to_evaluate_a_plan_the_user_may_not_see() {
    doThrow(new AuthorizationException(ErrorCode.BU_ORDER_NOT_AUTHORIZED)).when(budgetAuthorization)
        .checkAuthorized(plan);

    assertThatThrownBy(() -> service.evaluate(plan, DEC, false)).isInstanceOf(AuthorizationException.class);
  }

  // --- maintaining the calculation -------------------------------------------------------------

  @Test
  void adds_a_line_for_a_suborder_in_the_scope_of_the_plan() {
    service.storeLine(100L, new CalculationLineData(null, tree.suborderId("co/02/A"), categoryId("Senior"),
        new BigDecimal("7.5")));

    assertThat(plan.getCalculations()).singleElement().satisfies(line -> {
      assertThat(line.getSuborderId()).isEqualTo(tree.suborderId("co/02/A"));
      assertThat(line.getCalculatedHours()).isEqualTo(Duration.ofMinutes(450));
    });
    verify(orderBudgetRepository).save(plan);
  }

  @Test
  void refuses_a_suborder_outside_the_scope_of_the_plan() {
    assertRefused(new CalculationLineData(null, tree.suborderId("co/01"), categoryId("Senior"), BigDecimal.TEN),
        ErrorCode.BU_CALCULATION_SUBORDER_NOT_IN_SCOPE);
  }

  @Test
  void refuses_a_second_line_for_the_same_suborder_and_category() {
    addLine("co/02", "Senior", 10);

    assertRefused(new CalculationLineData(null, tree.suborderId("co/02"), categoryId("Senior"), BigDecimal.ONE),
        ErrorCode.BU_CALCULATION_LINE_EXISTS);
  }

  @Test
  void refuses_a_line_without_hours() {
    assertRefused(new CalculationLineData(null, tree.suborderId("co/02"), categoryId("Senior"), BigDecimal.ZERO),
        ErrorCode.BU_CALCULATION_HOURS_REQUIRED);
  }

  @Test
  void refuses_a_calculation_on_a_plan_without_fixed_price() {
    plan.setFixedPrice(false);

    assertRefused(new CalculationLineData(null, tree.suborderId("co/02"), categoryId("Senior"), BigDecimal.ONE),
        ErrorCode.BU_CALCULATION_NOT_FIXED_PRICE);
  }

  /** The write path checks the plan's order like the read path; the role sits on the annotation. */
  @Test
  void refuses_to_change_the_calculation_of_a_plan_the_user_may_not_see() {
    doThrow(new AuthorizationException(ErrorCode.BU_ORDER_NOT_AUTHORIZED)).when(budgetAuthorization)
        .checkAuthorized(plan);

    assertThatThrownBy(() -> service.storeLine(100L,
        new CalculationLineData(null, tree.suborderId("co/02"), categoryId("Senior"), BigDecimal.ONE)))
        .isInstanceOf(AuthorizationException.class);
    verify(orderBudgetRepository, never()).save(any());
  }

  @Test
  void changes_and_removes_an_existing_line() {
    var line = addLine("co/02", "Senior", 10);

    service.storeLine(100L, new CalculationLineData(line.getId(), tree.suborderId("co/02"), categoryId("Senior"),
        new BigDecimal("12")));
    assertThat(line.getCalculatedHours()).isEqualTo(Duration.ofHours(12));

    service.removeLine(100L, line.getId());
    assertThat(plan.getCalculations()).isEmpty();
  }

  // --- customer rates in the scope of a fixed price --------------------------------------------

  /** A rate above 0 EUR for the plan's suborder is named when the rate is saved; 0 EUR is not. */
  @Test
  void names_a_rate_above_zero_in_the_scope_of_a_fixed_price_plan_when_it_is_saved() {
    when(budgetAuthorization.isAuthorized(plan)).thenReturn(true);

    var notices = service.noticesForRate(rate("co/02", 9500));
    var none = service.noticesForRate(rate("co/02", 0));
    var outside = service.noticesForRate(rate("co/01", 9500));

    assertThat(notices).singleElement()
        .satisfies(notice -> assertThat(notice.getErrorCode()).isEqualTo(ErrorCode.BU_FIXED_PRICE_WITH_HOURLY_RATE));
    assertThat(none).isEmpty();
    assertThat(outside).isEmpty();
  }

  @Test
  void marks_the_rates_of_the_list_that_meet_a_fixed_price_plan() {
    when(budgetAuthorization.isAuthorized(plan)).thenReturn(true);
    when(orderBudgetRepository.findFixedPrice()).thenReturn(List.of(plan));
    var meeting = storedRate(1L, "co/02", 9500);
    var outside = storedRate(2L, "co/01", 9500);

    var byRate = service.getFixedPricePlanNamesByRateId(List.of(meeting, outside));

    assertThat(byRate).containsOnlyKeys(1L);
    assertThat(byRate.get(1L)).containsExactly("Relaunch");
  }

  // --- fixture ---------------------------------------------------------------------------------

  private List<SuborderReadModel> readModels() {
    return List.of("co/01", "co/02", "co/02/A").stream()
        .map(tree::suborder)
        .map(suborder -> new SuborderReadModel(suborder.getId(), suborder.getCustomerorder().getId(),
            OrderPosition.of(suborder).suborderPath(), suborder.getCompleteOrderSign(),
            suborder.getShortdescription(), Duration.ZERO, true, false))
        .toList();
  }

  private OrderBudgetCalculation addLine(String suborderSign, String category, int hours) {
    var line = new OrderBudgetCalculation();
    setId(line, 200L + plan.getCalculations().size());
    line.setOrderBudget(plan);
    line.setSuborder(tree.suborder(suborderSign));
    line.setCategory(CostCategoryTestUtils.named(category));
    line.setCalculatedHours(Duration.ofHours(hours));
    plan.getCalculations().add(line);
    return line;
  }

  private void booked(String suborderSign, long employeeId, int hours) {
    bookings.add(new PlanBooking(plan.getId(), tree.suborderId(suborderSign), employeeId,
        LocalDate.of(2026, 3, 10), Duration.ofHours(hours)));
  }

  private void progress(LocalDate refdate, int percent) {
    var entry = new OrderBudgetScopeEntry();
    entry.setOrderBudget(plan);
    entry.setRefdate(refdate);
    entry.setPercent(percent);
    plan.getScopeEntries().add(entry);
  }

  private OrderPricingData rate(String suborderSign, int cents) {
    return new OrderPricingData(tree.orderId("co"), suborderSign, null, null, null, cents, JAN, null);
  }

  private OrderPricing storedRate(long id, String suborderSign, int cents) {
    var rate = new OrderPricing();
    setId(rate, id);
    rate.setCustomerorder(tree.order("co"));
    rate.setSuborderSign(suborderSign);
    rate.setPriceCentsPerHour(cents);
    rate.setValidFrom(JAN);
    rate.setValidUntil(LocalDate.of(2999, 12, 31));
    return rate;
  }

  private void assertRefused(CalculationLineData data, ErrorCode code) {
    assertThatThrownBy(() -> service.storeLine(100L, data))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(code.getCode());
    verify(orderBudgetRepository, never()).save(any());
  }

  private static long categoryId(String name) {
    return CostCategoryTestUtils.named(name).getId();
  }

  private static CostCategory category(long id) {
    var category = new CostCategory("Senior");
    setId(category, id);
    return category;
  }

  private static void setId(AuditedEntity entity, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
  }
}
