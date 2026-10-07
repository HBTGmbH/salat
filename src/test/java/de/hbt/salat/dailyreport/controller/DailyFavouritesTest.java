package de.hbt.salat.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.dailyreport.domain.DailyViewData;
import de.hbt.salat.dailyreport.preferences.DailyPreferenceService;
import de.hbt.salat.dailyreport.preferences.DailyPreferences;
import de.hbt.salat.dailyreport.service.DailyService;
import de.hbt.salat.dailyreport.service.MatrixService;
import de.hbt.salat.dailyreport.service.TimereportService;
import de.hbt.salat.dailyreport.service.WorkingdayService;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.favorites.domain.FavoriteEntry;
import de.hbt.salat.favorites.domain.RecentFavorites;
import de.hbt.salat.favorites.service.FavoriteService;
import de.hbt.salat.order.service.EmployeeorderService;

/**
 * Die Favoriten sind die der angemeldeten Person (#1369). Wer als People Lead oder Manager den Tag
 * einer anderen Person ansieht, bekommt sie dort nicht angeboten (#1414).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class DailyFavouritesTest {

  private static final LocalDate DATE = LocalDate.of(2026, 3, 2);
  private static final long LOGIN_EMPLOYEE_ID = 1L;
  private static final long OWN_CONTRACT_ID = 10L;
  private static final long OTHER_CONTRACT_ID = 20L;

  @Mock private DailyService dailyService;
  @Mock private MatrixService matrixService;
  @Mock private TimereportService timereportService;
  @Mock private WorkingdayService workingdayService;
  @Mock private EmployeecontractService employeecontractService;
  @Mock private EmployeeService employeeService;
  @Mock private AuthorizedEmployee authorizedEmployee;
  @Mock private FavoriteService favoriteService;
  @Mock private EmployeeorderService employeeorderService;
  @Mock private MessageSourceAccessor messages;
  @Mock private ErrorCodeViewHelper errorCodeViewHelper;
  @Mock private DailyPreferenceService dailyPreferenceService;

  @InjectMocks private DailyController controller;

  @BeforeEach
  void setUp() {
    when(authorizedEmployee.getEmployeeId()).thenReturn(LOGIN_EMPLOYEE_ID);
    when(employeecontractService.getEmployeeIdOfEmployeecontract(OWN_CONTRACT_ID))
        .thenReturn(Optional.of(LOGIN_EMPLOYEE_ID));
    when(employeecontractService.getEmployeeIdOfEmployeecontract(OTHER_CONTRACT_ID))
        .thenReturn(Optional.of(2L));
    when(dailyService.buildDailyView(DATE, OWN_CONTRACT_ID)).thenReturn(mock(DailyViewData.class));
    when(dailyService.buildDailyView(DATE, OTHER_CONTRACT_ID)).thenReturn(mock(DailyViewData.class));
    when(dailyPreferenceService.getForEmployeeContractId(anyLong()))
        .thenReturn(new DailyPreferences(LocalTime.of(9, 0), true));
    var favourite = new FavoriteEntry(5L, 7L, "4711.01 - Konzept", 1, 0, "Abstimmung", List.of(),
        null, null, null);
    when(favoriteService.getRecentFavorites()).thenReturn(new RecentFavorites(List.of(favourite), 1));
  }

  @Test
  void the_own_day_offers_the_favourites() {
    var model = new ExtendedModelMap();

    controller.show(OWN_CONTRACT_ID, "daily", DATE, null, null, null, model);

    assertThat((List<?>) model.get("favorites")).hasSize(1);
    assertThat(model.get("favoriteCount")).isEqualTo(1);
  }

  @Test
  void the_day_of_somebody_else_offers_no_favourites() {
    var model = new ExtendedModelMap();

    controller.show(OTHER_CONTRACT_ID, "daily", DATE, null, null, null, model);

    assertThat((List<?>) model.get("favorites")).isEmpty();
  }

}
