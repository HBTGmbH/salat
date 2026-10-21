package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.dailyreport.auth.TimereportAuthorization;
import de.hbt.salat.dailyreport.domain.AccountBooking;
import de.hbt.salat.dailyreport.persistence.TimereportRepository;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeecontractDAO;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The bookings of „Meine Konten“ come as values, without a check per booking (#1514). The service checks the contract
 * instead, itself: whoever may not read every booking of it gets none.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportServiceAccountBookingsTest {

  private static final long CONTRACT_ID = 1L;
  private static final LocalDate FROM = LocalDate.parse("2026-01-01");
  private static final LocalDate UNTIL = LocalDate.parse("2026-12-31");

  @InjectMocks
  private TimereportService classUnderTest;

  @Mock
  private EmployeecontractDAO employeecontractDAO;
  @Mock
  private TimereportRepository timereportRepository;
  @Mock
  private TimereportAuthorization timereportAuthorization;

  private final Employeecontract contract = new Employeecontract();

  @BeforeEach
  void setUp() {
    when(employeecontractDAO.getEmployeecontractById(CONTRACT_ID)).thenReturn(contract);
  }

  @Test
  void a_readable_contract_yields_its_bookings() {
    when(timereportAuthorization.mayReadEveryBookingOf(contract)).thenReturn(true);
    var booking = new AccountBooking(2L, 3L, FROM, Duration.ofHours(8), false, "ORD/01", "TK", "");
    when(timereportRepository.findAccountBookingsOfContractBetween(CONTRACT_ID, FROM, UNTIL)).thenReturn(List.of(booking));

    assertThat(classUnderTest.getAccountBookingsOfContract(CONTRACT_ID, FROM, UNTIL)).containsExactly(booking);
  }

  @Test
  void a_contract_the_user_may_not_read_yields_nothing() {
    when(timereportAuthorization.mayReadEveryBookingOf(contract)).thenReturn(false);

    assertThatThrownBy(() -> classUnderTest.getAccountBookingsOfContract(CONTRACT_ID, FROM, UNTIL))
        .isInstanceOf(AuthorizationException.class);
    assertThatThrownBy(() -> classUnderTest.getAccountBookingsOfEmployeeorders(CONTRACT_ID, List.of(2L), FROM, UNTIL))
        .isInstanceOf(AuthorizationException.class);
    verify(timereportRepository, never()).findAccountBookingsOfContractBetween(anyLong(), any(), any());
    verify(timereportRepository, never()).findAccountBookingsOfEmployeeordersBetween(anyLong(), any(), any(), any());
  }

  @Test
  void an_unknown_contract_yields_nothing() {
    assertThatThrownBy(() -> classUnderTest.getAccountBookingsOfContract(99L, FROM, UNTIL))
        .isInstanceOf(AuthorizationException.class);
  }

  @Test
  void without_orders_the_database_is_not_asked() {
    when(timereportAuthorization.mayReadEveryBookingOf(contract)).thenReturn(true);

    assertThat(classUnderTest.getAccountBookingsOfEmployeeorders(CONTRACT_ID, List.of(), FROM, UNTIL)).isEmpty();
    verify(timereportRepository, never()).findAccountBookingsOfEmployeeordersBetween(anyLong(), any(), any(), any());
  }
}
