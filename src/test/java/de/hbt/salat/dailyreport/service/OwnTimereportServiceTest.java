package de.hbt.salat.dailyreport.service;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.dailyreport.domain.TimereportDTO;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.EmployeecontractPeriod;
import de.hbt.salat.employee.service.EmployeecontractService;

/**
 * The own bookings of a period (#1539): on one order or on all, in one order across a change of contract, and a page
 * at a time without repeating or skipping a booking.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class OwnTimereportServiceTest {

  private static final long EMPLOYEE_ID = 7L;
  private static final long OLD_CONTRACT = 10L;
  private static final long NEW_CONTRACT = 11L;
  private static final LocalDate FROM = of(2026, 1, 1);
  private static final LocalDate CHANGE = of(2026, 1, 16);
  private static final LocalDate UNTIL = of(2026, 1, 31);

  @Mock
  private EmployeecontractService employeecontractService;
  @Mock
  private TimereportService timereportService;
  @Mock
  private AuthorizedEmployee authorizedEmployee;
  @InjectMocks
  private OwnTimereportService ownTimereportService;

  private final List<TimereportDTO> oldContract = new ArrayList<>();
  private final List<TimereportDTO> newContract = new ArrayList<>();

  @BeforeEach
  void setUp() {
    when(authorizedEmployee.getEmployeeId()).thenReturn(EMPLOYEE_ID);
    when(employeecontractService.getEmployeecontractPeriodsBetween(EMPLOYEE_ID, FROM, UNTIL)).thenReturn(List.of(
        new EmployeecontractPeriod(OLD_CONTRACT, FROM, CHANGE.minusDays(1)),
        new EmployeecontractPeriod(NEW_CONTRACT, CHANGE, UNTIL)));
    when(timereportService.getTimereportsByDatesAndEmployeeContractId(OLD_CONTRACT, FROM, CHANGE.minusDays(1)))
        .thenReturn(oldContract);
    when(timereportService.getTimereportsByDatesAndEmployeeContractId(NEW_CONTRACT, CHANGE, UNTIL))
        .thenReturn(newContract);
  }

  /** Day, order, suborder, id — the same order whichever contract a booking hangs on. */
  @Test
  void the_bookings_stand_by_day_order_suborder_and_id_across_the_contracts() {
    newContract.add(booking(5, of(2026, 1, 20), "ABC-12", "ABC-12/01"));
    newContract.add(booking(4, of(2026, 1, 20), "ABC-12", "ABC-12/01"));
    oldContract.add(booking(3, of(2026, 1, 5), "XYZ-01", "XYZ-01/01"));
    oldContract.add(booking(2, of(2026, 1, 5), "ABC-12", "ABC-12/02"));
    oldContract.add(booking(1, of(2026, 1, 5), "ABC-12", "ABC-12/01"));

    assertThat(ids(ownTimereportService.getOwnTimereports(FROM, UNTIL, null, 0, Integer.MAX_VALUE)))
        .containsExactly(1L, 2L, 3L, 4L, 5L);
  }

  @Test
  void a_sign_keeps_the_bookings_on_that_order_with_all_its_suborders() {
    oldContract.add(booking(1, of(2026, 1, 5), "ABC-12", "ABC-12/01"));
    oldContract.add(booking(2, of(2026, 1, 5), "ABC-12", "ABC-12/01/03"));
    oldContract.add(booking(3, of(2026, 1, 6), "XYZ-01", "XYZ-01/01"));
    newContract.add(booking(4, of(2026, 1, 20), "ABC-12", "ABC-12/02"));

    assertThat(ids(ownTimereportService.getOwnTimereports(FROM, UNTIL, "ABC-12", 0, Integer.MAX_VALUE)))
        .containsExactly(1L, 2L, 4L);
  }

  /** The sign is compared exactly, as the database column compares it. */
  @Test
  void a_sign_in_another_spelling_or_unknown_finds_nothing() {
    oldContract.add(booking(1, of(2026, 1, 5), "ABC-12", "ABC-12/01"));

    assertThat(ownTimereportService.getOwnTimereports(FROM, UNTIL, "abc-12", 0, Integer.MAX_VALUE)).isEmpty();
    assertThat(ownTimereportService.getOwnTimereports(FROM, UNTIL, "NIX-99", 0, Integer.MAX_VALUE)).isEmpty();
  }

  /** Consecutive pages hold every booking once, also where day, order and suborder are alike. */
  @Test
  void consecutive_pages_hold_every_booking_once() {
    for (long id = 1; id <= 5; id++) {
      (id <= 3 ? oldContract : newContract).add(booking(id, id <= 3 ? of(2026, 1, 5) : of(2026, 1, 20), "ABC-12",
          "ABC-12/01"));
    }

    var pages = List.of(
        ownTimereportService.getOwnTimereports(FROM, UNTIL, null, 0, 2),
        ownTimereportService.getOwnTimereports(FROM, UNTIL, null, 2, 2),
        ownTimereportService.getOwnTimereports(FROM, UNTIL, null, 4, 2));

    assertThat(pages).extracting(OwnTimereportServiceTest::ids)
        .containsExactly(List.of(1L, 2L), List.of(3L, 4L), List.of(5L));
  }

  /** Offset and limit count the bookings the sign leaves. */
  @Test
  void the_page_is_cut_from_the_filtered_bookings() {
    oldContract.add(booking(1, of(2026, 1, 5), "XYZ-01", "XYZ-01/01"));
    oldContract.add(booking(2, of(2026, 1, 6), "ABC-12", "ABC-12/01"));
    oldContract.add(booking(3, of(2026, 1, 7), "XYZ-01", "XYZ-01/01"));
    newContract.add(booking(4, of(2026, 1, 20), "ABC-12", "ABC-12/01"));
    newContract.add(booking(5, of(2026, 1, 21), "ABC-12", "ABC-12/01"));

    assertThat(ids(ownTimereportService.getOwnTimereports(FROM, UNTIL, "ABC-12", 1, 1))).containsExactly(4L);
  }

  private static TimereportDTO booking(long id, LocalDate day, String orderSign, String completeOrderSign) {
    return TimereportDTO.builder().id(id).referenceday(day).customerorderSign(orderSign)
        .completeOrderSign(completeOrderSign).build();
  }

  private static List<Long> ids(List<TimereportDTO> timereports) {
    return timereports.stream().map(TimereportDTO::getId).toList();
  }
}
