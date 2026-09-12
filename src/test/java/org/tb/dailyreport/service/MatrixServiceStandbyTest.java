package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.dailyreport.domain.MatrixData;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.OrderType;

/**
 * Standby in the matrix overview (#463): its orders are listed below the sum row and take part in
 * no sum — neither in the daily ones nor in the total, SOLL and difference.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class MatrixServiceStandbyTest {

  private static final long CONTRACT_ID = 1L;
  private static final YearMonth MONTH = YearMonth.of(2026, 6);
  private static final LocalDate DAY = MONTH.atDay(15);

  @InjectMocks
  private MatrixService classUnderTest;
  @Mock
  private TimereportService timereportService;
  @Mock
  private PublicholidayService publicholidayService;
  @Mock
  private OvertimeService overtimeService;
  @Mock
  private WorkingdayService workingdayService;
  @Mock
  private EmployeecontractService employeecontractService;

  @BeforeEach
  void stubCollaborators() {
    lenient().when(publicholidayService.getPublicHolidaysBetween(any(), any())).thenReturn(List.of());
    lenient().when(workingdayService.getWorkingdaysByEmployeeContractId(anyLong(), any(), any())).thenReturn(List.of());
    lenient().when(timereportService.validateBeginOfWorkingDays(anyLong(), any(), any())).thenReturn(Map.of());
    lenient().when(timereportService.validateBreakTimes(anyLong(), any(), any())).thenReturn(Map.of());

    var contract = new Employeecontract();
    contract.setDailyWorkingTime(Duration.ofHours(8));
    lenient().when(employeecontractService.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);
    lenient().when(overtimeService.calculateWorkingTimeTarget(anyLong(), any(), any())).thenReturn(Duration.ofHours(160));
  }

  @Test
  void lists_a_standby_order_below_the_sum_row_and_not_above_it() {
    givenBookings(report(1L, "10001/01", OrderType.STANDARD, 8),
        report(2L, "10001/99", OrderType.BEREITSCHAFT, 12));

    var matrix = buildMatrix();

    assertThat(signsOf(matrix.rows())).containsExactly("10001/01");
    assertThat(signsOf(matrix.standbyRows())).containsExactly("10001/99");
  }

  @Test
  void keeps_the_row_total_of_a_standby_order() {
    givenBookings(report(2L, "10001/99", OrderType.BEREITSCHAFT, 12));

    assertThat(buildMatrix().standbyRows().getFirst().totalString()).isEqualTo("12:00");
  }

  @Test
  void leaves_standby_out_of_the_daily_sum_and_the_total() {
    givenBookings(report(1L, "10001/01", OrderType.STANDARD, 8),
        report(2L, "10001/99", OrderType.BEREITSCHAFT, 12));

    var matrix = buildMatrix();

    assertThat(matrix.totalString()).isEqualTo("8:00");
    assertThat(matrix.footerDays().get(DAY.getDayOfMonth() - 1).workingTimeString()).isEqualTo("8:00");
  }

  @Test
  void counts_a_day_of_standby_alone_as_a_day_without_working_time() {
    givenBookings(report(2L, "10001/99", OrderType.BEREITSCHAFT, 12));

    var matrix = buildMatrix();

    assertThat(matrix.totalString()).isEqualTo("0:00");
    assertThat(matrix.footerDays().get(DAY.getDayOfMonth() - 1).empty()).isTrue();
    // the full monthly target is missing, none of it worked off by the standby
    assertThat(matrix.diffString()).isEqualTo("-160:00");
  }

  private MatrixData buildMatrix() {
    return classUnderTest.buildMatrix(MONTH, CONTRACT_ID);
  }

  private void givenBookings(TimereportDTO... reports) {
    when(timereportService.getTimereportsByDatesAndEmployeeContractId(CONTRACT_ID, MONTH.atDay(1), MONTH.atEndOfMonth()))
        .thenReturn(List.of(reports));
  }

  private List<String> signsOf(List<MatrixData.Row> rows) {
    return rows.stream().map(MatrixData.Row::suborderSign).toList();
  }

  private TimereportDTO report(long suborderId, String sign, OrderType orderType, int hours) {
    return TimereportDTO.builder()
        .id(suborderId)
        .suborderId(suborderId)
        .completeOrderSign(sign)
        .customerorderSign("10001")
        .customerorderDescription("Auftrag")
        .customerShortname("TK")
        .suborderDescription("Unterauftrag")
        .orderType(orderType)
        .referenceday(DAY)
        .duration(Duration.ofHours(hours))
        .taskdescription("")
        .build();
  }

}
