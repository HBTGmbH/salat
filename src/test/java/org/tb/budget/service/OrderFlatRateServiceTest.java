package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tb.budget.domain.FlatRateRhythm;
import org.tb.budget.domain.OrderFlatRate;
import org.tb.budget.domain.OrderFlatRateData;
import org.tb.budget.domain.OrderFlatRateInstalmentData;
import org.tb.budget.domain.OrderFlatRateRow;
import org.tb.budget.persistence.OrderFlatRateRepository;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.test.FixedClock;
import org.tb.order.domain.Customerorder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Writing and listing flat rates (#972).
 *
 * <p>The rules worth pinning down are the ones that differ from the hourly rates next door: there is
 * no overlap check, because several definitions on one order add up on purpose; the suborder is a
 * concrete one rather than a pattern; and a monthly definition has to carry an end, or it would earn
 * without bound.
 */
@FixedClock("2026-06-25T10:15:30")
@DisplayNameGeneration(ReplaceUnderscores.class)
public class OrderFlatRateServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);
  private static final LocalDate YESTERDAY = TODAY.minusDays(1);
  private static final LocalDate TOMORROW = TODAY.plusDays(1);

  private OrderFlatRateRepository repository;
  private CustomerorderService customerorderService;
  private SuborderService suborderService;
  private OrderFlatRateService service;

  @BeforeEach
  public void setUp() {
    repository = mock(OrderFlatRateRepository.class);
    // A stored record carries an id — the create path returns it so the caller can go to the detail
    // page, where instalments are maintained.
    when(repository.save(any())).thenAnswer(i -> {
      OrderFlatRate saved = i.getArgument(0);
      setId(saved, 1L);
      return saved;
    });
    customerorderService = mock(CustomerorderService.class);
    // No orders unless a test says so: a definition whose order is gone stays reachable.
    when(customerorderService.getCustomerordersBySigns(any())).thenReturn(List.of());
    when(customerorderService.getCustomerorderBySign(any())).thenReturn(new Customerorder());
    suborderService = mock(SuborderService.class);
    when(suborderService.existsByCompleteOrderSign(anyString(), anyString())).thenReturn(true);
    service = new OrderFlatRateService(repository, suborderService, customerorderService);
  }

  // --- writing ---------------------------------------------------------------------------------

  @Test
  public void stores_a_one_off_flat_rate() {
    service.save(data(FlatRateRhythm.ONCE, TODAY, null, "1000"));

    var stored = savedFlatRate();
    assertThat(stored.getRhythm()).isEqualTo(FlatRateRhythm.ONCE);
    assertThat(stored.getAmount()).isEqualByComparingTo("1000");
  }

  /** A single amount is due on one day, so its end cannot say anything else. */
  @Test
  public void ends_a_one_off_flat_rate_on_its_own_due_date() {
    service.save(data(FlatRateRhythm.ONCE, TODAY, TODAY.plusYears(5), "1000"));

    assertThat(savedFlatRate().getValidUntil()).isEqualTo(TODAY);
  }

  /** An amount on an instalment definition would look like it earns something on its own. */
  @Test
  public void drops_the_amount_of_a_flat_rate_billed_in_instalments() {
    service.save(data(FlatRateRhythm.INSTALMENTS, TODAY, TOMORROW, "1000"));

    assertThat(savedFlatRate().getAmount()).isNull();
  }

  @Test
  public void refuses_a_monthly_flat_rate_without_an_amount() {
    assertThatThrownBy(() -> service.save(data(FlatRateRhythm.MONTHLY, TODAY, TOMORROW, null)))
        .isInstanceOf(BusinessRuleException.class)
        .extracting(e -> errorCodeOf((ErrorCodeException) e))
        .isEqualTo(ErrorCode.BU_FLAT_RATE_AMOUNT_REQUIRED);
    verify(repository, never()).save(any());
  }

  /** A zero amount earns nothing and is a half-entered record rather than an agreement. */
  @Test
  public void refuses_a_flat_rate_of_zero() {
    assertThatThrownBy(() -> service.save(data(FlatRateRhythm.ONCE, TODAY, null, "0")))
        .isInstanceOf(BusinessRuleException.class);
  }

  /** An unknown order sign resolves to nothing at all, and it does so silently (#958). */
  @Test
  public void refuses_an_unknown_customer_order() {
    when(customerorderService.getCustomerorderBySign(any())).thenReturn(null);

    assertThatThrownBy(() -> service.save(data(FlatRateRhythm.ONCE, TODAY, null, "1000")))
        .isInstanceOf(ErrorCodeException.class)
        .extracting(e -> errorCodeOf((ErrorCodeException) e))
        .isEqualTo(ErrorCode.BU_CUSTOMERORDER_SIGN_UNKNOWN);
  }

  @Test
  public void refuses_a_suborder_that_does_not_belong_to_the_order() {
    when(suborderService.existsByCompleteOrderSign(anyString(), anyString())).thenReturn(false);
    var data = new OrderFlatRateData("co", "other/01", null, FlatRateRhythm.ONCE,
        new BigDecimal("1000"), TODAY, TODAY);

    assertThatThrownBy(() -> service.save(data))
        .isInstanceOf(BusinessRuleException.class)
        .extracting(e -> errorCodeOf((ErrorCodeException) e))
        .isEqualTo(ErrorCode.BU_SUBORDER_NOT_IN_ORDER);
  }

  /** No suborder is the normal case: the flat rate then applies to the whole order. */
  @Test
  public void accepts_a_flat_rate_without_a_suborder() {
    service.save(data(FlatRateRhythm.ONCE, TODAY, null, "1000"));

    assertThat(savedFlatRate().isOrderWide()).isTrue();
    verify(suborderService, never()).existsByCompleteOrderSign(anyString(), anyString());
  }

  /**
   * Flat rates add up by design, so two definitions covering the same period are a legitimate case
   * — a monthly retainer next to the instalments of the same order. There is deliberately no
   * overlap rule to trip over.
   */
  @Test
  public void accepts_a_second_flat_rate_over_the_same_period() {
    service.save(data(FlatRateRhythm.MONTHLY, TODAY, TODAY.plusMonths(6), "100"));
    service.save(data(FlatRateRhythm.MONTHLY, TODAY, TODAY.plusMonths(6), "200"));

    verify(repository, times(2)).save(any());
  }

  /** Editing does not insist on the order: that is how a record whose order is gone gets corrected. */
  @Test
  public void updates_a_flat_rate_whose_customer_order_no_longer_exists() {
    var existing = flatRate("co", null, FlatRateRhythm.ONCE, TODAY, TODAY);
    when(repository.findById(1L)).thenReturn(Optional.of(existing));
    when(customerorderService.getCustomerorderBySign(any())).thenReturn(null);

    service.update(1L, data(FlatRateRhythm.ONCE, TOMORROW, null, "2000"));

    assertThat(existing.getValidFrom()).isEqualTo(TOMORROW);
  }

  // --- instalments -----------------------------------------------------------------------------

  @Test
  public void adds_an_instalment_inside_the_validity() {
    var existing = flatRate("co", null, FlatRateRhythm.INSTALMENTS, TODAY, TODAY.plusYears(1));
    when(repository.findById(1L)).thenReturn(Optional.of(existing));

    service.addInstalment(1L, new OrderFlatRateInstalmentData(new BigDecimal("500"), TOMORROW, "first"));

    assertThat(existing.getInstalments()).hasSize(1);
    assertThat(existing.getInstalments().get(0).getAmount()).isEqualByComparingTo("500");
  }

  /**
   * The validity is what the list, the filters and the controlling window judge the record by, so
   * an instalment outside it would be due while being invisible in all three.
   */
  @Test
  public void refuses_an_instalment_outside_the_validity() {
    var existing = flatRate("co", null, FlatRateRhythm.INSTALMENTS, TODAY, TODAY.plusMonths(1));
    when(repository.findById(1L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.addInstalment(1L,
        new OrderFlatRateInstalmentData(new BigDecimal("500"), YESTERDAY, null)))
        .isInstanceOf(BusinessRuleException.class)
        .extracting(e -> errorCodeOf((ErrorCodeException) e))
        .isEqualTo(ErrorCode.BU_FLAT_RATE_INSTALMENT_OUTSIDE_PERIOD);
  }

  @Test
  public void refuses_an_instalment_on_a_monthly_flat_rate() {
    var existing = flatRate("co", null, FlatRateRhythm.MONTHLY, TODAY, TODAY.plusYears(1));
    when(repository.findById(1L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.addInstalment(1L,
        new OrderFlatRateInstalmentData(new BigDecimal("500"), TOMORROW, null)))
        .isInstanceOf(BusinessRuleException.class)
        .extracting(e -> errorCodeOf((ErrorCodeException) e))
        .isEqualTo(ErrorCode.BU_FLAT_RATE_NOT_BILLED_IN_INSTALMENTS);
  }

  @Test
  public void refuses_to_remove_an_instalment_that_does_not_exist() {
    var existing = flatRate("co", null, FlatRateRhythm.INSTALMENTS, TODAY, TODAY.plusYears(1));
    when(repository.findById(1L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.removeInstalment(1L, 99L))
        .isInstanceOf(ErrorCodeException.class)
        .extracting(e -> errorCodeOf((ErrorCodeException) e))
        .isEqualTo(ErrorCode.BU_FLAT_RATE_INSTALMENT_NOT_FOUND);
  }

  // --- list filters ----------------------------------------------------------------------------

  /** Same two filters as the rate list, and they are independent of each other (#957). */
  @Test
  public void leaves_out_a_flat_rate_that_ended_yesterday() {
    given(flatRate("co", null, FlatRateRhythm.MONTHLY, TODAY.minusYears(1), YESTERDAY));

    assertThat(service.getRows(null, false, true)).isEmpty();
  }

  @Test
  public void shows_an_expired_flat_rate_when_asked_for() {
    given(flatRate("co", null, FlatRateRhythm.MONTHLY, TODAY.minusYears(1), YESTERDAY));

    assertThat(service.getRows(null, true, true)).hasSize(1);
  }

  @Test
  public void shows_a_flat_rate_that_only_starts_tomorrow() {
    given(flatRate("co", null, FlatRateRhythm.ONCE, TOMORROW, TOMORROW));

    assertThat(service.getRows(null, false, true)).hasSize(1);
  }

  /** The row carries the whole schedule, so the list can say what a monthly rate adds up to. */
  @Test
  public void reports_the_schedule_a_definition_amounts_to() {
    given(flatRate("co", null, FlatRateRhythm.MONTHLY, TODAY, TODAY.plusMonths(2), "100"));

    var row = service.getRows(null, false, true).get(0);

    assertThat(row.dueCount()).isEqualTo(3);
    assertThat(row.totalAmount()).isEqualByComparingTo("300");
    assertThat(row.isEmptySchedule()).isFalse();
  }

  /** Instalments not entered yet earn nothing, and the list marks that rather than hiding it. */
  @Test
  public void marks_a_definition_that_puts_nothing_on_the_calendar() {
    given(flatRate("co", null, FlatRateRhythm.INSTALMENTS, TODAY, TODAY.plusYears(1)));

    assertThat(service.getRows(null, false, true))
        .extracting(OrderFlatRateRow::isEmptySchedule).containsExactly(true);
  }

  // --- helpers ---------------------------------------------------------------------------------

  private void given(OrderFlatRate... flatRates) {
    when(repository.findAllByOrderByCustomerorderSignAscValidFromAsc()).thenReturn(List.of(flatRates));
  }

  private OrderFlatRate savedFlatRate() {
    var captor = ArgumentCaptor.forClass(OrderFlatRate.class);
    verify(repository, atLeastOnce()).save(captor.capture());
    return captor.getValue();
  }

  private static ErrorCode errorCodeOf(ErrorCodeException ex) {
    return ex.getMessages().get(0).getErrorCode();
  }

  private static OrderFlatRateData data(FlatRateRhythm rhythm, LocalDate from, LocalDate until, String amount) {
    return new OrderFlatRateData("co", null, "description", rhythm,
        amount == null ? null : new BigDecimal(amount), from, until);
  }

  private static OrderFlatRate flatRate(String customerorderSign, String suborderSign,
                                        FlatRateRhythm rhythm, LocalDate from, LocalDate until) {
    return flatRate(customerorderSign, suborderSign, rhythm, from, until, "100");
  }

  private static OrderFlatRate flatRate(String customerorderSign, String suborderSign,
                                        FlatRateRhythm rhythm, LocalDate from, LocalDate until,
                                        String amount) {
    var flatRate = new OrderFlatRate();
    setId(flatRate, 1L);
    flatRate.setCustomerorderSign(customerorderSign);
    flatRate.setSuborderSign(suborderSign);
    flatRate.setRhythm(rhythm);
    flatRate.setValidFrom(from);
    flatRate.setValidUntil(until);
    flatRate.setAmount(rhythm.hasOwnAmount() && amount != null ? new BigDecimal(amount) : null);
    return flatRate;
  }

  /** The id is generated, so there is no setter; a stored record always has one. */
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
