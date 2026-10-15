package de.hbt.salat.dailyreport.service;

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
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.dailyreport.domain.MatrixData;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.SurchargeKind;

/**
 * What a cell of the matrix overview knows about the bookings behind it: each booking with its
 * ticket references, which since #1326 are no part of the comment any more, and its time surcharge
 * (#1469). The credit of a day goes to a row of its own above the sum row.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class MatrixServiceDetailTest {

  private static final long CONTRACT_ID = 1L;
  private static final YearMonth MONTH = YearMonth.of(2026, 10);
  private static final LocalDate SATURDAY = MONTH.atDay(10);

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
  void a_booking_keeps_its_ticket_references_in_the_cell_details() {
    givenBookings(booking(1L, 2, "Einspielen").ticketReferences(List.of("ABC-12", "ABC-13")).build());

    var detail = cell().details().getFirst();

    assertThat(detail.ticketReferences()).containsExactly("ABC-12", "ABC-13");
    assertThat(detail.taskDescription()).isEqualTo("Einspielen");
  }

  @Test
  void a_flagged_booking_brings_its_surcharge_and_the_day_the_credit_of_all() {
    givenBookings(
        booking(1L, 3, "Einspielen").surchargeKind(SurchargeKind.SATURDAY).surchargePercent(50)
            .surchargeCredit(Duration.ofMinutes(90)).build(),
        booking(2L, 1, "Nacharbeit").build());

    var matrix = matrix();
    var cell = cell();

    assertThat(matrix.footerDays().get(SATURDAY.getDayOfMonth() - 1).surchargeCredit()).isEqualTo(Duration.ofMinutes(90));
    assertThat(matrix.footerDays().get(SATURDAY.getDayOfMonth()).surchargeCredit()).isZero();
    assertThat(matrix.surchargeCreditTotal()).isEqualTo(Duration.ofMinutes(90));
    assertThat(cell.details()).extracting(MatrixData.ReportDetail::surchargeKind)
        .containsExactly(SurchargeKind.SATURDAY, null);
    assertThat(cell.details().getFirst().surchargePercent()).isEqualTo(50);
    assertThat(cell.details().getFirst().surchargeCredit()).isEqualTo(Duration.ofMinutes(90));
  }

  @Test
  void a_month_without_a_flagged_booking_has_no_credit() {
    givenBookings(booking(1L, 2, "Einspielen").build());

    assertThat(matrix().surchargeCreditTotal()).isZero();
    assertThat(matrix().footerDays()).hasSize(MONTH.lengthOfMonth())
        .allSatisfy(day -> assertThat(day.surchargeCredit()).isZero());
  }

  /**
   * The time surcharge counts against the target, as on the time account: the difference of the month is booked plus
   * surcharge minus target, while the booked total stays the booked time.
   */
  @Test
  void the_difference_counts_the_surcharge_against_the_target() {
    givenBookings(booking(1L, 3, "Einspielen").surchargeKind(SurchargeKind.SATURDAY).surchargePercent(50)
        .surchargeCredit(Duration.ofMinutes(90)).build());

    var matrix = matrix();

    assertThat(matrix.totalString()).isEqualTo("3:00");
    assertThat(matrix.diffString()).isEqualTo("-155:30");
  }

  /* "Stand gestern" counts the credit up to yesterday: a credit after it is no part of it yet. */
  @Test
  @FixedClock("2026-10-12T10:00:00")
  void the_balance_of_yesterday_counts_the_surcharge_up_to_yesterday() {
    givenBookings(
        booking(1L, 3, "Einspielen").surchargeKind(SurchargeKind.SATURDAY).surchargePercent(50)
            .surchargeCredit(Duration.ofMinutes(90)).build(),
        booking(2L, 2, "Störung").referenceday(MONTH.atDay(20)).surchargeKind(SurchargeKind.NIGHT).surchargePercent(25)
            .surchargeCredit(Duration.ofMinutes(30)).build());
    when(overtimeService.calculateWorkingTimeTarget(CONTRACT_ID, MONTH.atDay(1), MONTH.atDay(11)))
        .thenReturn(Duration.ofHours(56));

    // 3:00 booked + 1:30 credit - 56:00 target
    assertThat(matrix().prevDayDiffString()).isEqualTo("-51:30");
  }

  @Test
  void a_flagged_standby_booking_earns_no_credit_in_the_matrix() {
    givenBookings(booking(1L, 3, "Rufbereitschaft").orderType(OrderType.BEREITSCHAFT).surchargeKind(SurchargeKind.SATURDAY)
        .surchargePercent(50).surchargeCredit(Duration.ofMinutes(90)).build());

    var matrix = matrix();

    assertThat(matrix.surchargeCreditTotal()).isZero();
    assertThat(matrix.diffString()).isEqualTo("-160:00");
  }

  private MatrixData matrix() {
    return classUnderTest.buildMatrix(MONTH, CONTRACT_ID);
  }

  private MatrixData.Cell cell() {
    return matrix().rows().getFirst().cells().get(SATURDAY.getDayOfMonth() - 1);
  }

  private void givenBookings(TimereportDTO... reports) {
    when(timereportService.getTimereportsByDatesAndEmployeeContractId(CONTRACT_ID, MONTH.atDay(1), MONTH.atEndOfMonth()))
        .thenReturn(List.of(reports));
  }

  private static TimereportDTO.TimereportDTOBuilder booking(long id, int hours, String comment) {
    return TimereportDTO.builder()
        .id(id)
        .suborderId(10L)
        .completeOrderSign("10001/01")
        .customerorderSign("10001")
        .customerorderDescription("Auftrag")
        .customerShortname("TK")
        .suborderDescription("Unterauftrag")
        .orderType(OrderType.STANDARD)
        .referenceday(SATURDAY)
        .duration(Duration.ofHours(hours))
        .taskdescription(comment);
  }
}
