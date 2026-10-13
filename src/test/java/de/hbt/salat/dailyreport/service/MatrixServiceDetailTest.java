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

    assertThat(matrix.surchargeDays().get(SATURDAY.getDayOfMonth() - 1)).isEqualTo(Duration.ofMinutes(90));
    assertThat(matrix.surchargeDays().get(SATURDAY.getDayOfMonth())).isZero();
    assertThat(matrix.surchargeTotal()).isEqualTo(Duration.ofMinutes(90));
    assertThat(cell.details()).extracting(MatrixData.ReportDetail::surchargeKind)
        .containsExactly(SurchargeKind.SATURDAY, null);
    assertThat(cell.details().getFirst().surchargePercent()).isEqualTo(50);
    assertThat(cell.details().getFirst().surchargeCredit()).isEqualTo(Duration.ofMinutes(90));
  }

  @Test
  void a_month_without_a_flagged_booking_has_no_credit() {
    givenBookings(booking(1L, 2, "Einspielen").build());

    assertThat(matrix().surchargeTotal()).isZero();
    assertThat(matrix().surchargeDays()).hasSize(MONTH.lengthOfMonth()).allSatisfy(credit -> assertThat(credit).isZero());
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
