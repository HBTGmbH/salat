package de.hbt.salat.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
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
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.ui.ExtendedModelMap;
import de.hbt.salat.common.beta.Betas;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.dailyreport.domain.FavoriteShortList;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.dailyreport.preferences.DailyPreferences;
import de.hbt.salat.dailyreport.service.DailyService;
import de.hbt.salat.dailyreport.service.FavoriteShortListService;
import de.hbt.salat.dailyreport.service.MatrixService;
import de.hbt.salat.dailyreport.service.PublicholidayService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.favorites.service.FavoriteService;
import de.hbt.salat.order.service.EmployeeorderService;

/**
 * Die Tagesansicht blättert in zwei Schrittweiten (#1541): ‹ und › gehen einen Kalendertag, « und » zum
 * nächstgelegenen Arbeitstag, ohne Wochenende und Feiertage. Welcher Tag das ist, sagt der
 * {@link PublicholidayService}; die Fälle stehen in seinen Tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DailyDayNavigationTest {

  /** Ein Freitag: der nächste Kalendertag ist ein Samstag, der nächste Arbeitstag der Montag. */
  private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 23);

  @Mock private DailyService dailyService;
  @Mock private MatrixService matrixService;
  @Mock private TimereportService timereportService;
  @Mock private WorkingdayService workingdayService;
  @Mock private PublicholidayService publicholidayService;
  @Mock private EmployeecontractService employeecontractService;
  @Mock private EmployeeService employeeService;
  @Mock private FavoriteService favoriteService;
  @Mock private FavoriteShortListService favoriteShortListService;
  @Mock private Betas betas;
  @Mock private EmployeeorderService employeeorderService;
  @Mock private MessageSourceAccessor messages;
  @Mock private ErrorCodeViewHelper errorCodeViewHelper;
  @Mock private DailyPreferenceService dailyPreferenceService;

  @InjectMocks private DailyController controller;

  @BeforeEach
  void setUp() {
    var loginEmployee = mock(Employee.class);
    when(loginEmployee.getId()).thenReturn(1L);
    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    when(employeecontractService.getCurrentContract(1L)).thenReturn(Optional.empty());
    when(dailyPreferenceService.getForEmployeeContractId(anyLong())).thenReturn(new DailyPreferences(LocalTime.of(9, 0), true));
    when(favoriteShortListService.getForCurrentUser()).thenReturn(FavoriteShortList.none());
    when(publicholidayService.getLastWorkdayBefore(FRIDAY)).thenReturn(LocalDate.of(2026, 10, 22));
    when(publicholidayService.getFirstWorkdayAfter(FRIDAY)).thenReturn(LocalDate.of(2026, 10, 26));
  }

  @Test
  void the_single_steps_go_one_calendar_day() {
    var model = new ExtendedModelMap();

    controller.show(null, "daily", FRIDAY, null, null, null, model);

    assertThat(model.get("prevDate")).isEqualTo(LocalDate.of(2026, 10, 22));
    assertThat(model.get("nextDate")).isEqualTo(LocalDate.of(2026, 10, 24));
  }

  @Test
  void the_larger_steps_go_to_the_nearest_working_day() {
    var model = new ExtendedModelMap();

    controller.show(null, "daily", FRIDAY, null, null, null, model);

    assertThat(model.get("prevWorkday")).isEqualTo(LocalDate.of(2026, 10, 22));
    assertThat(model.get("nextWorkday")).isEqualTo(LocalDate.of(2026, 10, 26));
  }
}
